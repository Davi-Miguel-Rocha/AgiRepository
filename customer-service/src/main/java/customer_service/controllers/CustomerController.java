package customer_service.controllers;

import customer_service.dtos.CustomerRequestDTO;
import customer_service.dtos.CustomerResponseDTO;
import customer_service.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Customer", description = "Operações relacionadas ao serviço de clientes do sistema")
@RestController
@RequestMapping("/customers")
public class CustomerController { //POST, GET. PUT. DELETE

    @Autowired
    private CustomerService customerService;


    @Operation(
            summary = "Cadastrar um novo cliente",
            description = "Cria um novo cliente na base de dados, com base nas informações fornecidas no corpo da requisição."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Cliente criado com sucesso",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CustomerResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Requisição inválida - verifique os dados enviados",
                    content = @Content
            )
    })
    @PostMapping
    public ResponseEntity<CustomerResponseDTO> create(@RequestBody @Valid CustomerRequestDTO requestDTO) {
        CustomerResponseDTO response = customerService.save(requestDTO);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


    @Operation(
            summary = "Buscar um cliente por Id",
            description = "Busca um cliente específico na base de dados, com base no Id fornecido na requisição."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Cliente localizado!",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = CustomerResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Cliente não encontrado.",
                    content = @Content
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponseDTO> findById(@PathVariable Long id) {
        CustomerResponseDTO responseDTO = customerService.findById(id);
        return ResponseEntity.ok(responseDTO);
    }


    @Operation(
            summary = "Buscar todos os clientes cadastrados na base, com paginação",
            description = "Retorna uma página de clientes cadastrados na base de dados."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Lista de clientes retornada com sucesso",
                    content = @Content(
                            mediaType = "application/json",
                            // Retorna lista de objetos do tipo CustomerResponseDTO, em formato JSON
                            array = @ArraySchema(schema = @Schema(implementation = CustomerResponseDTO.class))
                    )
            )
    })
    @GetMapping("/all")
    public ResponseEntity<Page<CustomerResponseDTO>> findAll(@ParameterObject Pageable pageable) {
        Page<CustomerResponseDTO> customers = customerService.findAll(pageable);
        return ResponseEntity.ok(customers);
    }


    @Operation(
            summary = "Atualizar os dados de um cliente por Id",
            description = "Busca um cliente existente na base de dados por Id, e atualiza seus dados de cadastro.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody( // Anotação do Swagger para detalhar o corpo a ser recebido na requisição
                    description = "Dados atualizados do cliente",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CustomerRequestDTO.class)
                    )
            )
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Cliente atualizado com sucesso",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CustomerResponseDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Cliente não encontrado.",
                    content = @Content
            )
    })
    @PutMapping("/{id}")
    public ResponseEntity<CustomerResponseDTO> update(
            @PathVariable Long id, @RequestBody @Valid CustomerRequestDTO requestDTO
    ) {
        CustomerResponseDTO customer = customerService.update(id, requestDTO);

        return ResponseEntity.ok(customer);
    }


    @Operation(
            summary = "Remover (soft delete) cliente por Id",
            description = "Marca o cliente como deletado (soft delete), definindo o campo 'deletedAt'. O registro não é removido fisicamente do banco. Operação idempotente. "
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Cliente marcado como deletado (No Content)",
                    content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = CustomerResponseDTO.class)
                    )

            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Cliente não encontrado.",
                    content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id){

        customerService.delete(id);

        return ResponseEntity.noContent().build();
    }


}
