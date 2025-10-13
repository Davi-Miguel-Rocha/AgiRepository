package analytic_service.controllers;

import analytic_service.dto.AnalyticResponseCompletedDTO;
import analytic_service.dto.AnalyticResponseDefaultDTO;
import analytic_service.model.Analytic;
import analytic_service.services.AnalyticService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Analytic", description = "Operações relacionadas ao serviço de análise do sistema")
@RestController
@RequestMapping("/analytic")
public class AnalyticController {

    private final AnalyticService analyticService;

    public AnalyticController(AnalyticService analyticService) {
        this.analyticService = analyticService;
    }


    @Operation(
            summary = "Cadastrar uma nova análise",
            description = "Cria uma nova análise vinculada a um customerId (chave lógica) específico."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Análise cadastrada com sucesso.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AnalyticResponseCompletedDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "200",
                    description = "Análise registrada, mas ainda em processamento.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AnalyticResponseDefaultDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Cliente não encontrado.",
                    content = @Content
            )
    })
    @PostMapping("/{customerId}")
    public ResponseEntity<AnalyticResponseDefaultDTO> create(@PathVariable Long customerId){
        AnalyticResponseDefaultDTO response = analyticService.save(customerId);

        if (response instanceof AnalyticResponseCompletedDTO) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } else {
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }
    }


    @Operation(
            summary = "Exibir todas as análises de um cliente",
            description = "Retorna uma lista com todas as análises vinculadas a um customerId constantes na base de dados."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Lista de análises retornada com sucesso",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = Analytic.class))
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Cliente não encontrado",
                    content = @Content(schema = @Schema())
            )
    })
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<?>> findByCustomer(@PathVariable Long customerId) {
        List<?> response = analyticService.findByCustomerId(customerId);

            return ResponseEntity.status(HttpStatus.OK).body(response);

    }

}
