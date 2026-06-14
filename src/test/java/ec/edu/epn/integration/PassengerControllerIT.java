package ec.edu.epn.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.epn.dto.PassengerRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Transactional
class PassengerControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ── helpers ───────────────────────────────────────────────────────────────

    private PassengerRequest buildRequest(String firstName, String lastName, String email, String passport) {
        PassengerRequest req = new PassengerRequest();
        req.setFirstName(firstName);
        req.setLastName(lastName);
        req.setEmail(email);
        req.setPassportNumber(passport);
        return req;
    }

    private Long createPassenger(String firstName, String lastName, String email, String passport) throws Exception {
        PassengerRequest req = buildRequest(firstName, lastName, email, passport);
        MvcResult result = mockMvc.perform(post("/api/passengers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    // ── shouldCreatePassenger ─────────────────────────────────────────────────
    @Test
    void shouldCreatePassenger() throws Exception {
        PassengerRequest req = buildRequest("Juan", "Perez", "juan.perez@epn.edu.ec", "PA123456");

        mockMvc.perform(post("/api/passengers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.firstName").value("Juan"))
                .andExpect(jsonPath("$.lastName").value("Perez"))
                .andExpect(jsonPath("$.email").value("juan.perez@epn.edu.ec"))
                .andExpect(jsonPath("$.passportNumber").value("PA123456"));
    }

    // ── shouldRejectDuplicateEmail ────────────────────────────────────────────
    @Test
    void shouldRejectDuplicateEmail() throws Exception {
        createPassenger("Juan", "Perez", "duplicado@epn.edu.ec", "PA111111");

        PassengerRequest duplicate = buildRequest("Carlos", "Lopez", "duplicado@epn.edu.ec", "PA222222");
        mockMvc.perform(post("/api/passengers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().is4xxClientError());
    }

    // ── shouldFindAllPassengers ───────────────────────────────────────────────
    @Test
    void shouldFindAllPassengers() throws Exception {
        createPassenger("Ana", "Garcia", "ana.garcia@epn.edu.ec", "PA333333");
        createPassenger("Luis", "Torres", "luis.torres@epn.edu.ec", "PA444444");

        mockMvc.perform(get("/api/passengers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[*].email",
                        hasItems("ana.garcia@epn.edu.ec", "luis.torres@epn.edu.ec")));
    }

    // ── shouldFindPassengerById ───────────────────────────────────────────────
    @Test
    void shouldFindPassengerById() throws Exception {
        Long id = createPassenger("Maria", "Andrade", "maria.andrade@epn.edu.ec", "PA555555");

        mockMvc.perform(get("/api/passengers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.firstName").value("Maria"));
    }

    // ── shouldFindPassengerByEmail ────────────────────────────────────────────
    @Test
    void shouldFindPassengerByEmail() throws Exception {
        createPassenger("Pedro", "Vega", "pedro.vega@epn.edu.ec", "PA666666");

        mockMvc.perform(get("/api/passengers/email/{email}", "pedro.vega@epn.edu.ec"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("pedro.vega@epn.edu.ec"))
                .andExpect(jsonPath("$.firstName").value("Pedro"));
    }

    // ── shouldFindPassengerByPassportNumber ───────────────────────────────────
    @Test
    void shouldFindPassengerByPassportNumber() throws Exception {
        createPassenger("Sofia", "Mora", "sofia.mora@epn.edu.ec", "PA777777");

        mockMvc.perform(get("/api/passengers/passport/{passportNumber}", "PA777777"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passportNumber").value("PA777777"))
                .andExpect(jsonPath("$.firstName").value("Sofia"));
    }

    // ── shouldReturn404WhenPassengerNotFound ──────────────────────────────────
    @Test
    void shouldReturn404WhenPassengerNotFound() throws Exception {
        mockMvc.perform(get("/api/passengers/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── shouldUpdatePassenger ─────────────────────────────────────────────────
    @Test
    void shouldUpdatePassenger() throws Exception {
        Long id = createPassenger("Roberto", "Salas", "roberto.salas@epn.edu.ec", "PA888888");

        PassengerRequest updated = buildRequest("Roberto", "Salas Actualizado", "roberto.salas@epn.edu.ec", "PA888888");
        mockMvc.perform(put("/api/passengers/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Salas Actualizado"));
    }

    // ── shouldDeletePassenger ─────────────────────────────────────────────────
    @Test
    void shouldDeletePassenger() throws Exception {
        Long id = createPassenger("Temporal", "User", "temporal.user@epn.edu.ec", "PA999999");

        mockMvc.perform(delete("/api/passengers/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/passengers/{id}", id))
                .andExpect(status().isNotFound());
    }

    // ── shouldRejectInvalidEmail ──────────────────────────────────────────────
    @Test
    void shouldRejectInvalidEmail() throws Exception {
        PassengerRequest invalid = buildRequest("Test", "User", "esto-no-es-un-email", "PA000001");

        mockMvc.perform(post("/api/passengers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}