package ec.edu.epn.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.epn.dto.AirportRequest;
import ec.edu.epn.dto.FlightRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Transactional
class FlightControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private Long originId;
    private Long destinationId;

    @BeforeEach
    void setUp() throws Exception {
        originId = createAirport("Aeropuerto Mariscal Sucre", "UIO", "Quito", "Ecuador");
        destinationId = createAirport("Aeropuerto Olmedo", "GYE", "Guayaquil", "Ecuador");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Long createAirport(String name, String code, String city, String country) throws Exception {
        AirportRequest req = new AirportRequest();
        req.setName(name);
        req.setCode(code);
        req.setCity(city);
        req.setCountry(country);
        MvcResult result = mockMvc.perform(post("/api/airports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private FlightRequest buildFlightRequest(String number, LocalDateTime dep, LocalDateTime arr, String st) {
        FlightRequest req = new FlightRequest();
        req.setFlightNumber(number);
        req.setOriginId(originId);
        req.setDestinationId(destinationId);
        req.setDepartureTime(dep);
        req.setArrivalTime(arr);
        req.setStatus(st);
        return req;
    }

    private Long createFlight(String number, LocalDateTime dep, LocalDateTime arr, String status) throws Exception {
        FlightRequest req = buildFlightRequest(number, dep, arr, status);
        MvcResult result = mockMvc.perform(post("/api/flights")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    // ── shouldCreateFlight ────────────────────────────────────────────────────
    @Test
    void shouldCreateFlight() throws Exception {
        LocalDateTime dep = LocalDateTime.now().plusDays(1);
        LocalDateTime arr = dep.plusHours(1);
        FlightRequest req = buildFlightRequest("EPN-001", dep, arr, "SCHEDULED");

        mockMvc.perform(post("/api/flights")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.flightNumber").value("EPN-001"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    // ── shouldRejectDuplicateFlightNumber ─────────────────────────────────────
    @Test
    void shouldRejectDuplicateFlightNumber() throws Exception {
        LocalDateTime dep = LocalDateTime.now().plusDays(1);
        createFlight("EPN-DUP", dep, dep.plusHours(1), "SCHEDULED");

        FlightRequest duplicate = buildFlightRequest("EPN-DUP", dep.plusDays(1), dep.plusDays(1).plusHours(1), "SCHEDULED");
        mockMvc.perform(post("/api/flights")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ── shouldRejectArrivalBeforeDeparture ────────────────────────────────────
    @Test
    void shouldRejectArrivalBeforeDeparture() throws Exception {
        LocalDateTime dep = LocalDateTime.now().plusDays(1);
        LocalDateTime arrBeforeDep = dep.minusHours(2);

        FlightRequest req = buildFlightRequest("EPN-INV", dep, arrBeforeDep, "SCHEDULED");
        mockMvc.perform(post("/api/flights")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ── shouldFindAllFlights ──────────────────────────────────────────────────
    @Test
    void shouldFindAllFlights() throws Exception {
        LocalDateTime dep = LocalDateTime.now().plusDays(1);
        createFlight("EPN-A01", dep, dep.plusHours(1), "SCHEDULED");
        createFlight("EPN-A02", dep.plusDays(1), dep.plusDays(1).plusHours(2), "SCHEDULED");

        mockMvc.perform(get("/api/flights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[*].flightNumber", hasItems("EPN-A01", "EPN-A02")));
    }

    // ── shouldFindFlightById ──────────────────────────────────────────────────
    @Test
    void shouldFindFlightById() throws Exception {
        LocalDateTime dep = LocalDateTime.now().plusDays(1);
        Long id = createFlight("EPN-B01", dep, dep.plusHours(1), "SCHEDULED");

        mockMvc.perform(get("/api/flights/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.flightNumber").value("EPN-B01"));
    }

    // ── shouldFindFlightByFlightNumber ────────────────────────────────────────
    @Test
    void shouldFindFlightByFlightNumber() throws Exception {
        LocalDateTime dep = LocalDateTime.now().plusDays(1);
        createFlight("EPN-C01", dep, dep.plusHours(1), "SCHEDULED");

        mockMvc.perform(get("/api/flights/number/{flightNumber}", "EPN-C01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flightNumber").value("EPN-C01"));
    }

    // ── shouldFindFlightsByStatus ─────────────────────────────────────────────
    @Test
    void shouldFindFlightsByStatus() throws Exception {
        LocalDateTime dep = LocalDateTime.now().plusDays(1);
        createFlight("EPN-D01", dep, dep.plusHours(1), "BOARDING");
        createFlight("EPN-D02", dep.plusDays(1), dep.plusDays(1).plusHours(1), "SCHEDULED");

        mockMvc.perform(get("/api/flights/status/{status}", "BOARDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].status", everyItem(is("BOARDING"))));
    }

    // ── shouldFindFlightsBetweenDates ─────────────────────────────────────────
    @Test
    void shouldFindFlightsBetweenDates() throws Exception {
        LocalDateTime dep = LocalDateTime.now().plusDays(2);
        createFlight("EPN-E01", dep, dep.plusHours(1), "SCHEDULED");

        LocalDateTime start = dep.minusDays(1);
        LocalDateTime end = dep.plusDays(1);

        mockMvc.perform(get("/api/flights/between")
                        .param("start", start.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        .param("end", end.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[*].flightNumber", hasItem("EPN-E01")));
    }

    // ── shouldUpdateFlight ────────────────────────────────────────────────────
    @Test
    void shouldUpdateFlight() throws Exception {
        LocalDateTime dep = LocalDateTime.now().plusDays(1);
        Long id = createFlight("EPN-F01", dep, dep.plusHours(1), "SCHEDULED");

        FlightRequest updated = buildFlightRequest("EPN-F01", dep, dep.plusHours(1), "BOARDING");
        mockMvc.perform(put("/api/flights/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOARDING"));
    }

    // ── shouldReturn404WhenFlightNotFound ─────────────────────────────────────
    @Test
    void shouldReturn404WhenFlightNotFound() throws Exception {
        mockMvc.perform(get("/api/flights/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── shouldDeleteFlight ────────────────────────────────────────────────────
    @Test
    void shouldDeleteFlight() throws Exception {
        LocalDateTime dep = LocalDateTime.now().plusDays(1);
        Long id = createFlight("EPN-G01", dep, dep.plusHours(1), "SCHEDULED");

        mockMvc.perform(delete("/api/flights/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/flights/{id}", id))
                .andExpect(status().isNotFound());
    }
}