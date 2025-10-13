package analytic_service.services;

import analytic_service.dto.AnalyticResponseDefaultDTO;
import analytic_service.dto.CustomerResponseDTO;
import analytic_service.dto.HistoryResponseDTO;
import analytic_service.exceptions.ServiceNotFoundException;
import analytic_service.mapper.AnalyticMapper;
import analytic_service.model.Analytic;
import analytic_service.repositories.AnalyticRepository;
import analytic_service.utils.GenerateVector;
import analytic_service.utils.WebClientUtil;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class AnalyticService {
    private static final Logger log = LoggerFactory.getLogger(AnalyticService.class);

    private final AnalyticRepository analyticRepository;
    private final ScoreService scoreService;
    private final WebClientUtil webClientUtil;
    private final GenerateVector generateVector;
    private final AnalyticMapper analyticMapper;

    public AnalyticService(
            AnalyticRepository analyticRepository,
            ScoreService scoreService,
            WebClientUtil webClientUtil,
            GenerateVector generateVector, AnalyticMapper analyticMapper) {
        this.analyticRepository = analyticRepository;
        this.scoreService = scoreService;
        this.webClientUtil = webClientUtil;
        this.generateVector = generateVector;
        this.analyticMapper = analyticMapper;
        log.info("Serviços injetados com sucesso");
    }

    @Transactional
    public AnalyticResponseDefaultDTO save(Long customerId) {
        CustomerResponseDTO customer = findCustomer(customerId);
        LocalDate onCreateDate = customer.getOnCreate().toLocalDate();

        try {
            List<HistoryResponseDTO> histories = findHistories(customerId);

            int score = 0;
            // Verifica se o cliente tem menos de 30 dias ou não possui histórico
            if (onCreateDate.isAfter(LocalDate.now().minusDays(30)) || histories.isEmpty()) {
                log.info("Cliente novo ou sem histórico. Score inicial = 300");
                log.info("Dados do customer: " + customer);
                log.info("Historico " + histories);

                score = 300;

            } else {
                double[] data = generateVector.generateData(histories, customer);
                log.info("Dados do vetor gerado: " + Arrays.toString(data));
                log.info("Dados do customer: " + customer);

                score = scoreService.calc(data);

            }

            LocalDateTime startDate = LocalDate.now().minusDays(10).atStartOfDay();
            //Faz a busca no banco para ver se esse cliente já tem alguma análise nos últimos 10 dias
            //Se tiver o retorno será essa análise, não faz uma nova persistência
            Optional<Analytic> optionalAnalytic = analyticRepository.findByCustomerIdLastTenDays(customerId, startDate);
            if (optionalAnalytic.isPresent()) {
                if ("CONCLUIDO".equals(optionalAnalytic.get().getStatus())) {
                    return analyticMapper.toCompleteDto(optionalAnalytic.get(), onCreateDate);
                } else if ("PROCESSANDO".equals(optionalAnalytic.get().getStatus())) {
                    // Setar os dados calculados com sucesso
                    optionalAnalytic.get().setScore(score);
                    String typeOfRisk = getTypeOfRisk(score);
                    optionalAnalytic.get().setTypeOfRisk(typeOfRisk);
                    optionalAnalytic.get().setStatus("CONCLUIDO");

                    // Salva a entidade existente
                    analyticRepository.save(optionalAnalytic.get());

                    return analyticMapper.toCompleteDto(optionalAnalytic.get(), onCreateDate);
                }
            }

            String typeOfRisk = getTypeOfRisk(score);
            Analytic entity = new Analytic(customerId, score, typeOfRisk);
            entity.setStatus("CONCLUIDO");
            analyticRepository.save(entity);

            return analyticMapper.toCompleteDto(entity, onCreateDate);
        } catch (ObjectOptimisticLockingFailureException ignored) {
            //isso aqui é usado para evitar erros de threads quando usamos jobs agendados
            return save(customerId);

        } catch (WebClientResponseException.InternalServerError ex) {
            return saveDefault(customerId, customer);
        }
    }

    @Transactional
    public AnalyticResponseDefaultDTO saveDefault(Long customerId, CustomerResponseDTO customer) {
        LocalDate onCreateDate = customer.getOnCreate().toLocalDate();
        LocalDateTime startDate = LocalDate.now().minusDays(10).atStartOfDay();

        Optional<Analytic> optionalAnalytic = analyticRepository.findByCustomerIdLastTenDays(customerId, startDate);
        if (optionalAnalytic.isPresent()) {

            return optionalAnalytic.get().getStatus().equals("CONCLUIDO") ? analyticMapper.toCompleteDto(optionalAnalytic.get(), onCreateDate)
                    : analyticMapper.toDefaultDto(optionalAnalytic.get(), onCreateDate);

        }
        Analytic entity = new Analytic(customerId);
        analyticRepository.save(entity);

        return analyticMapper.toDefaultDto(entity, onCreateDate);
    }

    @Scheduled(fixedRateString = "10s")
    public void scheduledReprocessPending() {
        log.info("INICIANDO JOB AGENDADO: Verificando análises pendentes.");

        // 1. Busca todos os registros "PROCESSANDO" de todos os clientes
        List<Analytic> pendingAnalytics = analyticRepository.findByStatus("PROCESSANDO");

        if (pendingAnalytics.isEmpty()) {
            log.info("JOB AGENDADO: Nenhuma análise PROCESSANDO encontrada.");
            return;
        }
        for (Analytic entity : pendingAnalytics) {
            try {
                // Chama o método que tenta concluir o processamento
                reprocessSingleAnalytic(entity);
            } catch (ObjectOptimisticLockingFailureException ignored) {
                log.info("Sendo tratado por outra thread - ignorado");
            } catch (Exception e) {
                log.error("Falha CRÍTICA ao tentar reprocessar a Análise ID {}. Cliente ID: {}",
                        entity.getId(), entity.getClientId(), e);
            }
        }
    }

    @Transactional
    public void reprocessSingleAnalytic(Analytic entity) {
        Long customerId = entity.getClientId();

        CustomerResponseDTO customer = findCustomer(customerId); // Pode lançar exceção
        LocalDate onCreateDate = customer.getOnCreate().toLocalDate();

        try {
            List<HistoryResponseDTO> histories = findHistories(customerId); // Pode lançar WebClientResponseException
            int score = 0;

            if (onCreateDate.isAfter(LocalDate.now().minusDays(30)) || histories.isEmpty()) {
                score = 300;
            } else {
                double[] data = generateVector.generateData(histories, customer);
                score = scoreService.calc(data);
            }

            String typeOfRisk = getTypeOfRisk(score);
            entity.setScore(score);
            entity.setTypeOfRisk(typeOfRisk);
            entity.setStatus("CONCLUIDO");

            analyticRepository.save(entity);

        } catch (WebClientResponseException.InternalServerError ex) {
            log.warn("Falha no History-Service durante o reprocessamento. Tentativa falhou.");

        }
    }

    private String getTypeOfRisk(int score) {
        String typeOfRisk = "";
        if (score < 350) {
            typeOfRisk = "ALTO RISCO";
        } else if (score < 699) {
            typeOfRisk = "MEDIO RISCO";
        } else {
            typeOfRisk = "BAIXO RISCO";
        }
        return typeOfRisk;
    }

    private List<HistoryResponseDTO> findHistories(Long customerId) {
        String uri = "/history/" + customerId + "/last-year";
        return webClientUtil.getList(uri, HistoryResponseDTO.class);

    }

    private CustomerResponseDTO findCustomer(Long customerId) {
        try {
            String uri = "/customers/" + customerId;
            return webClientUtil.get(uri, CustomerResponseDTO.class);


        } catch (WebClientResponseException.NotFound ex) {
            throw new EntityNotFoundException("Cliente " + customerId + " não encontrado", ex);

        } catch (WebClientResponseException.InternalServerError | WebClientRequestException ex) {
            throw new ServiceNotFoundException("Customer-Service indisponível");
        }
    }

    @Transactional(readOnly = true)
    public List<?> findByCustomerId(Long customerId) {
        List<Analytic> analytics = analyticRepository.findByCustomerId(customerId);
        List<?> dtos = analytics.stream().map(
                x -> x.getStatus().equals("CONCLUIDO") ? analyticMapper.toCompleteDto(x, x.getOnCreate().toLocalDate())
                        : analyticMapper.toDefaultDto(x, x.getOnCreate().toLocalDate())

        ).toList();

        if (analytics.isEmpty()) {
            throw new EntityNotFoundException("Não foram encontradas análises para o cliente " + customerId);
        }

        return dtos;
    }
}




