package com.example.banking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BankingApplicationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void customerCanRegisterLoginDepositWithdrawAndUpdateProfile() throws Exception {
        AuthenticatedCustomer customer = registerAndLogin("flow");
        mockMvc.perform(post("/api/accounts")
                        .session(customer.session())
                        .contentType(APPLICATION_JSON)
                        .content("{\"accountType\":\"SAVINGS\",\"balance\":0}"))
                .andExpect(status().isForbidden());

        Long accountId = createAccount(customer, 10);

        mockMvc.perform(post("/api/accounts/{id}/deposit", accountId)
                        .session(customer.session())
                        .header("X-CSRF-TOKEN", customer.csrfToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"amount\":0.001}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/accounts/{id}/deposit", accountId)
                        .session(customer.session())
                        .header("X-CSRF-TOKEN", customer.csrfToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"amount\":20.00,\"description\":\"Demo deposit\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DEPOSIT"));

        mockMvc.perform(post("/api/accounts/{id}/withdraw", accountId)
                        .session(customer.session())
                        .header("X-CSRF-TOKEN", customer.csrfToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"amount\":5.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("WITHDRAWAL"));

        mockMvc.perform(post("/api/accounts/{id}/withdraw", accountId)
                        .session(customer.session())
                        .header("X-CSRF-TOKEN", customer.csrfToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"amount\":100.00}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/accounts/{id}", accountId).session(customer.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(25.0));

        mockMvc.perform(get("/api/transactions").session(customer.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].direction").value("OUTGOING"));

        mockMvc.perform(get("/api/dashboard/summary").session(customer.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBalance").value(25.0))
                .andExpect(jsonPath("$.activeAccounts").value(1));
        mockMvc.perform(get("/api/dashboard/monthly-summary").session(customer.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));
        mockMvc.perform(get("/api/dashboard/recent-transactions").session(customer.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].direction").value("OUTGOING"));

        mockMvc.perform(put("/api/auth/me")
                        .session(customer.session())
                        .header("X-CSRF-TOKEN", customer.csrfToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"email\":\"UPDATED@example.com\",\"firstName\":\"New\",\"lastName\":\"Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("updated@example.com"));

        mockMvc.perform(post("/api/auth/logout")
                        .session(customer.session())
                        .header("X-CSRF-TOKEN", customer.csrfToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recipientCanReadTransferButCannotWithdrawFromSendersAccount() throws Exception {
        AuthenticatedCustomer sender = registerAndLogin("sender");
        AuthenticatedCustomer recipient = registerAndLogin("recipient");
        Long senderAccountId = createAccount(sender, 50);
        Long recipientAccountId = createAccount(recipient, 0);

        MvcResult transferResult = mockMvc.perform(post("/api/transfers")
                        .session(sender.session())
                        .header("X-CSRF-TOKEN", sender.csrfToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"sourceAccountId\":" + senderAccountId
                                + ",\"targetAccountId\":" + recipientAccountId
                                + ",\"amount\":12.50,\"description\":\"Test transfer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direction").value("OUTGOING"))
                .andReturn();
        long transactionId = objectMapper.readTree(transferResult.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(get("/api/transactions/{id}", transactionId).session(recipient.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direction").value("INCOMING"));

        mockMvc.perform(get("/api/accounts/{id}", senderAccountId).session(recipient.session()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/accounts/{id}/withdraw", senderAccountId)
                        .session(recipient.session())
                        .header("X-CSRF-TOKEN", recipient.csrfToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"amount\":1.00}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void concurrentWithdrawalsCannotOverdrawAnAccount() throws Exception {
        AuthenticatedCustomer customer = registerAndLogin("concurrent");
        Long accountId = createAccount(customer, 100);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            java.util.concurrent.Callable<Integer> withdrawal = () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start concurrent withdrawals");
                }
                return mockMvc.perform(post("/api/accounts/{id}/withdraw", accountId)
                                .session(customer.session())
                                .header("X-CSRF-TOKEN", customer.csrfToken())
                                .contentType(APPLICATION_JSON)
                                .content("{\"amount\":80.00}"))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            };
            var first = executor.submit(withdrawal);
            var second = executor.submit(withdrawal);
            assertTrue(ready.await(5, TimeUnit.SECONDS), "Both withdrawal requests should be ready");
            start.countDown();

            int firstStatus = first.get(10, TimeUnit.SECONDS);
            int secondStatus = second.get(10, TimeUnit.SECONDS);
            assertTrue((firstStatus == 200 && secondStatus == 400)
                    || (firstStatus == 400 && secondStatus == 200));
            mockMvc.perform(get("/api/accounts/{id}", accountId).session(customer.session()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(20.0));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private AuthenticatedCustomer registerAndLogin(String prefix) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String username = prefix + "-" + suffix;
        String password = "change-this-demo-password";
        MockHttpSession session = new MockHttpSession();
        String csrfToken = getCsrfToken(session);

        mockMvc.perform(post("/api/auth/register")
                        .session(session)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username
                                + "\",\"email\":\"" + username + "@example.com\""
                                + ",\"password\":\"" + password
                                + "\",\"firstName\":\"Demo\",\"lastName\":\"User\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"invalid-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));

        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk());

        return new AuthenticatedCustomer(session, getCsrfToken(session));
    }

    private Long createAccount(AuthenticatedCustomer customer, int initialBalance) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/accounts")
                        .session(customer.session())
                        .header("X-CSRF-TOKEN", customer.csrfToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"accountType\":\"SAVINGS\",\"balance\":" + initialBalance + ".00}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String getCsrfToken(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("token").asText();
    }

    private record AuthenticatedCustomer(MockHttpSession session, String csrfToken) {
    }
}
