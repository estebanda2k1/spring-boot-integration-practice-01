package ec.edu.epn.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.epn.dto.AirportRequest;
import org.junit.jupiter.api.BeforeEach;
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
class AirportControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private AirportRequest buildRequest(String name, String code, String city, String country) {
        AirportRequest req = new AirportRequest();
        req.setName(name);
        req.setCode(code);
        req.setCity(city);
        req.setCountry(country);
        return req;
    }

    private Long createAirport(String name, String code, String city, String country) throws Exception {
        AirportRequest req = buildRequest(name, code, city, country);
        MvcResult result = mockMvc.perform(post("/api/airports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    // ── shouldCreateAirport ───────────────────────────────────────────────────
    @Test
    void shouldCreateAirport() throws Exception {
        AirportRequest req = buildRequest("Aeropuerto Internacional Mariscal Sucre", "UIO", "Quito", "Ecuador");

        mockMvc.perform(post("/api/airports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.code").value("UIO"))
                .andExpect(jsonPath("$.city").value("Quito"))
                .andExpect(jsonPath("$.country").value("Ecuador"));
    }

    // ── shouldRejectDuplicateAirportCode ─────────────────────────────────────
    @Test
    void shouldRejectDuplicateAirportCode() throws Exception {
        createAirport("Aeropuerto Mariscal Sucre", "UIO", "Quito", "Ecuador");

        AirportRequest duplicate = buildRequest("Otro Aeropuerto", "UIO", "Quito", "Ecuador");
        mockMvc.perform(post("/api/airports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().is4xxClientError());
    }

    // ── shouldFindAllAirports ─────────────────────────────────────────────────
    @Test
    void shouldFindAllAirports() throws Exception {
        createAirport("Aeropuerto Mariscal Sucre", "UIO", "Quito", "Ecuador");
        createAirport("Aeropuerto Jose Joaquin de Olmedo", "GYE", "Guayaquil", "Ecuador");

        mockMvc.perform(get("/api/airports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[*].code", hasItems("UIO", "GYE")));
    }

    // ── shouldFindAirportById ─────────────────────────────────────────────────
    @Test
    void shouldFindAirportById() throws Exception {
        Long id = createAirport("Aeropuerto Mariscal Sucre", "UIO", "Quito", "Ecuador");

        mockMvc.perform(get("/api/airports/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.code").value("UIO"))
                .andExpect(jsonPath("$.city").value("Quito"));
    }

    // ── shouldFindAirportByCode ───────────────────────────────────────────────
    @Test
    void shouldFindAirportByCode() throws Exception {
        createAirport("Aeropuerto Mariscal Sucre", "UIO", "Quito", "Ecuador");

        mockMvc.perform(get("/api/airports/code/{code}", "UIO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("UIO"))
                .andExpect(jsonPath("$.name").value("Aeropuerto Mariscal Sucre"));
    }

    // ── shouldReturn404WhenAirportNotFound ────────────────────────────────────
    @Test
    void shouldReturn404WhenAirportNotFound() throws Exception {
        mockMvc.perform(get("/api/airports/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── shouldUpdateAirport ───────────────────────────────────────────────────
    @Test
    void shouldUpdateAirport() throws Exception {
        Long id = createAirport("Aeropuerto Mariscal Sucre", "UIO", "Quito", "Ecuador");

        AirportRequest updated = buildRequest("Aeropuerto Internacional de Quito Actualizado", "UIO", "Quito", "Ecuador");
        mockMvc.perform(put("/api/airports/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Aeropuerto Internacional de Quito Actualizado"));
    }

    // ── shouldDeleteAirport ───────────────────────────────────────────────────
    @Test
    void shouldDeleteAirport() throws Exception {
        Long id = createAirport("Aeropuerto Temporal", "TMP", "Ciudad", "Pais");

        mockMvc.perform(delete("/api/airports/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/airports/{id}", id))
                .andExpect(status().isNotFound());
    }

    // ── shouldRejectInvalidAirportRequest ─────────────────────────────────────
    @Test
    void shouldRejectInvalidAirportRequest() throws Exception {
        // code vacío, name vacío → debe fallar validación
        AirportRequest invalid = buildRequest("", "AB", "", "");

        mockMvc.perform(post("/api/airports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}