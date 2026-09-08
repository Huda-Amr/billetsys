/*
 * Eclipse Public License - v 2.0
 *
 *   THE ACCOMPANYING PROGRAM IS PROVIDED UNDER THE TERMS OF THIS ECLIPSE
 *   PUBLIC LICENSE ("AGREEMENT"). ANY USE, REPRODUCTION OR DISTRIBUTION
 *   OF THE PROGRAM CONSTITUTES RECIPIENT'S ACCEPTANCE OF THIS AGREEMENT.
 */

package ai.mnemosyne_systems.resource;

import ai.mnemosyne_systems.model.Company;
import ai.mnemosyne_systems.model.Ticket;
import ai.mnemosyne_systems.model.User;
import ai.mnemosyne_systems.model.UserAvailability;
import ai.mnemosyne_systems.service.UserAvailabilityService;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.response.Response;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class UserAvailabilityApiResourceTest extends AccessTestSupport {

    private static final String BASE_PATH = "/api/availability";

    private static final String USER_EMAIL = "avail-user@testing.com";
    private static final String USER2_EMAIL = "avail-user-2@testing.com";
    private static final String USER_B_EMAIL = "avail-user-b@testing.com";
    private static final String NO_COMPANY_EMAIL = "avail-nocompany@testing.com";
    private static final String TAM_EMAIL = "avail-tam@testing.com";
    private static final String TAM2_EMAIL = "avail-tam-2@testing.com";
    private static final String SUPPORT_EMAIL = "avail-support@testing.com";
    private static final String SUPPORT2_EMAIL = "avail-support-2@testing.com";
    private static final String SUPERUSER_EMAIL = "avail-superuser@testing.com";
    private static final String SUPERUSER2_EMAIL = "avail-superuser-2@testing.com";

    private static final String COMPANY_A = "Availability Company A";
    private static final String COMPANY_B = "Availability Company B";

    @Inject
    UserAvailabilityService service;

    private final List<Long> createdTicketIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        QuarkusTransaction.requiringNew().run(() -> {
            for (Long id : createdTicketIds) {
                Ticket ticket = Ticket.findById(id);
                if (ticket != null) {
                    ticket.delete();
                }
            }
            List<String> emails = List.of(USER_EMAIL, USER2_EMAIL, USER_B_EMAIL, NO_COMPANY_EMAIL, TAM_EMAIL,
                    TAM2_EMAIL, SUPPORT_EMAIL, SUPPORT2_EMAIL, SUPERUSER_EMAIL, SUPERUSER2_EMAIL);
            for (String email : emails) {
                User user = User.find("email", email).firstResult();
                if (user != null) {
                    UserAvailability.delete("user", user);
                }
            }
            for (String companyName : List.of(COMPANY_A, COMPANY_B)) {
                Company company = Company.find("name", companyName).firstResult();
                if (company != null) {
                    UserAvailability.delete("company", company);
                }
            }
        });
        createdTicketIds.clear();
    }

    private void seedUsers() {
        ensureUser("avail-user", USER_EMAIL, User.TYPE_USER);
        ensureUser("avail-user-2", USER2_EMAIL, User.TYPE_USER);
        ensureUser("avail-user-b", USER_B_EMAIL, User.TYPE_USER);
        ensureUser("avail-nocompany", NO_COMPANY_EMAIL, User.TYPE_USER);
        ensureUser("avail-tam", TAM_EMAIL, User.TYPE_TAM);
        ensureUser("avail-tam-2", TAM2_EMAIL, User.TYPE_TAM);
        ensureUser("avail-support", SUPPORT_EMAIL, User.TYPE_SUPPORT);
        ensureUser("avail-support-2", SUPPORT2_EMAIL, User.TYPE_SUPPORT);
        ensureUser("avail-superuser", SUPERUSER_EMAIL, User.TYPE_SUPERUSER);
        ensureUser("avail-superuser-2", SUPERUSER2_EMAIL, User.TYPE_SUPERUSER);
    }

    private Company companyA() {
        Long companyId = ensureCompany(COMPANY_A);
        ensureCompanyUsers(companyId, USER_EMAIL, USER2_EMAIL, TAM_EMAIL, TAM2_EMAIL, SUPPORT_EMAIL, SUPERUSER_EMAIL);
        return Company.findById(companyId);
    }

    private Company companyB() {
        Long companyId = ensureCompany(COMPANY_B);
        ensureCompanyUsers(companyId, USER_B_EMAIL, SUPPORT2_EMAIL, SUPERUSER2_EMAIL);
        return Company.findById(companyId);
    }

    private UserAvailability createPersonalAvailabilityDirect(User user, Company company, LocalDate start,
            LocalDate end, String reason) {

        UserAvailability[] holder = new UserAvailability[1];
        QuarkusTransaction.requiringNew().run(() -> holder[0] = service.createPersonal(User.findById(user.id),
                Company.findById(company.id), start, end, reason));
        return holder[0];
    }

    private UserAvailability createCompanyAvailabilityDirect(User user, Company company, LocalDate start, LocalDate end,
            String reason) {

        UserAvailability[] holder = new UserAvailability[1];
        QuarkusTransaction.requiringNew().run(() -> holder[0] = service.createCompany(User.findById(user.id),
                Company.findById(company.id), start, end, reason));
        return holder[0];
    }

    private Ticket createTicketWithParticipants(Company company, User support, User tam, User regularUser) {
        Ticket[] holder = new Ticket[1];
        QuarkusTransaction.requiringNew().run(() -> {
            Ticket ticket = new Ticket();
            ticket.name = "TKT-" + UUID.randomUUID();
            ticket.status = "Open";
            ticket.company = Company.findById(company.id);
            ticket.supportUsers.add(User.findById(support.id));
            ticket.tamUsers.add(User.findById(tam.id));
            ticket.userUsers.add(User.findById(regularUser.id));
            ticket.persist();
            holder[0] = ticket;
        });
        createdTicketIds.add(holder[0].id);
        return holder[0];
    }

    private Map<String, Object> requestBody(LocalDate start, LocalDate end, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("startDate", start == null ? null : start.toString());
        body.put("endDate", end == null ? null : end.toString());
        body.put("reason", reason);
        return body;
    }

    @Test
    void getMyAvailability_withoutAuthentication_returnsUnauthorized() {
        given().when().get(BASE_PATH + "/me").then().statusCode(401);
    }

    @Test
    void createPersonal_withoutAuthentication_returnsUnauthorized() {
        given().contentType("application/json").body(requestBody(LocalDate.now(), LocalDate.now().plusDays(1), "PTO"))
                .when().post(BASE_PATH).then().statusCode(401);
    }

    @Test
    @TestSecurity(user = USER2_EMAIL, roles = "user")
    @JwtSecurity(claims = { @Claim(key = "email", value = USER2_EMAIL), @Claim(key = "sub", value = "avail-user-2") })
    void update_byDifferentUser_isRejectedAndLeavesRecordUnchanged() {
        seedUsers();
        Company company = companyA();
        User owner = User.find("email", USER_EMAIL).firstResult();

        LocalDate originalStart = LocalDate.now().plusDays(10);
        LocalDate originalEnd = LocalDate.now().plusDays(15);
        UserAvailability availability = createPersonalAvailabilityDirect(owner, company, originalStart, originalEnd,
                "Vacation");

        Response response = given().contentType("application/json")
                .body(requestBody(originalStart.plusDays(1), originalEnd.plusDays(1), "Hijacked")).when()
                .put(BASE_PATH + "/" + availability.id);

        assertEquals(401, response.getStatusCode());

        UserAvailability reloaded = UserAvailability.findById(availability.id);
        assertNotNull(reloaded);
        assertEquals(originalStart, reloaded.startDate);
        assertEquals(originalEnd, reloaded.endDate);
    }

    @Test
    @TestSecurity(user = USER2_EMAIL, roles = "user")
    @JwtSecurity(claims = { @Claim(key = "email", value = USER2_EMAIL), @Claim(key = "sub", value = "avail-user-2") })
    void delete_byDifferentUser_isRejectedAndRecordStillExists() {
        seedUsers();
        Company company = companyA();
        User owner = User.find("email", USER_EMAIL).firstResult();

        UserAvailability availability = createPersonalAvailabilityDirect(owner, company, LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(15), "Vacation");

        Response response = given().when().delete(BASE_PATH + "/" + availability.id);

        assertEquals(401, response.getStatusCode());
        assertNotNull(UserAvailability.findById(availability.id));
    }

    @Test
    @TestSecurity(user = USER_EMAIL, roles = "user")
    @JwtSecurity(claims = { @Claim(key = "email", value = USER_EMAIL), @Claim(key = "sub", value = "avail-user") })
    void createCompanyWide_byRegularUser_isRejected() {
        seedUsers();
        Company company = companyA();

        Response response = given().contentType("application/json")
                .body(requestBody(LocalDate.now().plusDays(1), LocalDate.now().plusDays(2), "Company retreat")).when()
                .post(BASE_PATH + "/company");

        assertEquals(401, response.getStatusCode());

        List<UserAvailability> companyAvailability = service.getForCompany(company);
        assertTrue(companyAvailability.isEmpty(), "No company availability should have been created");
    }

    @Test
    @TestSecurity(user = SUPPORT_EMAIL, roles = "support")
    @JwtSecurity(claims = { @Claim(key = "email", value = SUPPORT_EMAIL),
            @Claim(key = "sub", value = "avail-support") })
    void createCompanyWide_bySupport_isAllowed() {
        seedUsers();
        Company company = companyA();

        Response response = given().contentType("application/json")
                .body(requestBody(LocalDate.now().plusDays(1), LocalDate.now().plusDays(2), "Company retreat")).when()
                .post(BASE_PATH + "/company");

        response.then().statusCode(201);

        List<UserAvailability> companyAvailability = service.getForCompany(company);
        assertEquals(1, companyAvailability.size());
    }

    @Test
    @TestSecurity(user = USER_EMAIL, roles = "user")
    @JwtSecurity(claims = { @Claim(key = "email", value = USER_EMAIL), @Claim(key = "sub", value = "avail-user") })
    void createPersonal_withValidData_returns201AndPersists() {
        seedUsers();
        Company company = companyA();
        User user = User.find("email", USER_EMAIL).firstResult();

        LocalDate start = LocalDate.now().plusDays(5);
        LocalDate end = LocalDate.now().plusDays(8);

        Response response = given().contentType("application/json").body(requestBody(start, end, "Vacation")).when()
                .post(BASE_PATH);

        response.then().statusCode(201);

        assertEquals(start.toString(), response.jsonPath().getString("startDate"));
        assertEquals(end.toString(), response.jsonPath().getString("endDate"));

        List<UserAvailability> stored = service.getForUser(user);
        assertEquals(1, stored.size());
        assertEquals("Vacation", stored.get(0).reason);
    }

    @Test
    @TestSecurity(user = NO_COMPANY_EMAIL, roles = "user")
    @JwtSecurity(claims = { @Claim(key = "email", value = NO_COMPANY_EMAIL),
            @Claim(key = "sub", value = "avail-nocompany") })
    void createPersonal_whenUserHasNoCompany_returns400() {
        seedUsers();

        given().contentType("application/json").body(requestBody(LocalDate.now(), LocalDate.now().plusDays(1), "PTO"))
                .when().post(BASE_PATH).then().statusCode(400);
    }

    @Test
    @TestSecurity(user = USER_EMAIL, roles = "user")
    @JwtSecurity(claims = { @Claim(key = "email", value = USER_EMAIL), @Claim(key = "sub", value = "avail-user") })
    void update_byOwner_changesDates() {
        seedUsers();
        Company company = companyA();
        User user = User.find("email", USER_EMAIL).firstResult();

        UserAvailability availability = createPersonalAvailabilityDirect(user, company, LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(15), "Vacation");

        LocalDate newStart = LocalDate.now().plusDays(12);
        LocalDate newEnd = LocalDate.now().plusDays(18);

        given().contentType("application/json").body(requestBody(newStart, newEnd, "Extended vacation")).when()
                .put(BASE_PATH + "/" + availability.id).then().statusCode(200);

        UserAvailability reloaded = UserAvailability.findById(availability.id);
        assertEquals(newStart, reloaded.startDate);
        assertEquals(newEnd, reloaded.endDate);
        assertEquals("Extended vacation", reloaded.reason);
    }

    @Test
    @TestSecurity(user = USER_EMAIL, roles = "user")
    @JwtSecurity(claims = { @Claim(key = "email", value = USER_EMAIL), @Claim(key = "sub", value = "avail-user") })
    void delete_byOwner_returns204AndRemovesRecord() {
        seedUsers();
        Company company = companyA();
        User user = User.find("email", USER_EMAIL).firstResult();

        UserAvailability availability = createPersonalAvailabilityDirect(user, company, LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(15), "Vacation");

        given().when().delete(BASE_PATH + "/" + availability.id).then().statusCode(204);

        assertEquals(null, UserAvailability.findById(availability.id));
    }

    @Test
    @TestSecurity(user = USER_EMAIL, roles = "user")
    @JwtSecurity(claims = { @Claim(key = "email", value = USER_EMAIL), @Claim(key = "sub", value = "avail-user") })
    void createPersonal_overlappingExistingRange_isRejected() {
        seedUsers();
        Company company = companyA();
        User user = User.find("email", USER_EMAIL).firstResult();

        LocalDate existingStart = LocalDate.now().plusDays(10);
        LocalDate existingEnd = LocalDate.now().plusDays(15);
        createPersonalAvailabilityDirect(user, company, existingStart, existingEnd, "Vacation");

        Response response = given().contentType("application/json")
                .body(requestBody(existingStart.plusDays(3), existingEnd.plusDays(3), "Second trip")).when()
                .post(BASE_PATH);

        assertEquals(400, response.getStatusCode());

        List<UserAvailability> stored = service.getForUser(user);
        assertEquals(1, stored.size(), "Only the original availability should exist");
    }

    @Test
    @TestSecurity(user = USER_EMAIL, roles = "user")
    @JwtSecurity(claims = { @Claim(key = "email", value = USER_EMAIL), @Claim(key = "sub", value = "avail-user") })
    void createPersonal_nonOverlappingRanges_bothSucceed() {
        seedUsers();
        Company company = companyA();
        User user = User.find("email", USER_EMAIL).firstResult();

        LocalDate existingStart = LocalDate.now().plusDays(10);
        LocalDate existingEnd = LocalDate.now().plusDays(15);
        createPersonalAvailabilityDirect(user, company, existingStart, existingEnd, "Vacation");

        given().contentType("application/json")
                .body(requestBody(existingStart.minusDays(9), existingStart.minusDays(1), "Earlier trip")).when()
                .post(BASE_PATH).then().statusCode(201);

        given().contentType("application/json")
                .body(requestBody(existingEnd.plusDays(1), existingEnd.plusDays(5), "Later trip")).when()
                .post(BASE_PATH).then().statusCode(201);

        List<UserAvailability> stored = service.getForUser(user);
        assertEquals(3, stored.size());
    }

    @Test
    @TestSecurity(user = USER_EMAIL, roles = "user")
    @JwtSecurity(claims = { @Claim(key = "email", value = USER_EMAIL), @Claim(key = "sub", value = "avail-user") })
    void updatePersonal_toOverlapAnotherRange_isRejected() {
        seedUsers();
        Company company = companyA();
        User user = User.find("email", USER_EMAIL).firstResult();

        UserAvailability first = createPersonalAvailabilityDirect(user, company, LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(5), "First trip");
        createPersonalAvailabilityDirect(user, company, LocalDate.now().plusDays(10), LocalDate.now().plusDays(15),
                "Second trip");

        Response response = given().contentType("application/json")
                .body(requestBody(LocalDate.now().plusDays(1), LocalDate.now().plusDays(12), "Stretched")).when()
                .put(BASE_PATH + "/" + first.id);

        assertEquals(400, response.getStatusCode());

        UserAvailability reloaded = UserAvailability.findById(first.id);
        assertEquals(LocalDate.now().plusDays(5), reloaded.endDate, "Original range must remain unchanged");
    }

    @Test
    @TestSecurity(user = USER_EMAIL, roles = "user")
    @JwtSecurity(claims = { @Claim(key = "email", value = USER_EMAIL), @Claim(key = "sub", value = "avail-user") })
    void createPersonal_endDateBeforeStartDate_isRejected() {
        seedUsers();
        Company company = companyA();
        User user = User.find("email", USER_EMAIL).firstResult();

        Response response = given().contentType("application/json")
                .body(requestBody(LocalDate.now().plusDays(10), LocalDate.now().plusDays(5), "Invalid")).when()
                .post(BASE_PATH);

        assertEquals(400, response.getStatusCode());

        assertTrue(service.getForUser(user).isEmpty());
    }

    @Test
    @TestSecurity(user = USER_EMAIL, roles = "user")
    @JwtSecurity(claims = { @Claim(key = "email", value = USER_EMAIL), @Claim(key = "sub", value = "avail-user") })
    void createPersonal_missingStartDate_isRejected() {
        seedUsers();
        companyA();

        Response response = given().contentType("application/json")
                .body(requestBody(null, LocalDate.now().plusDays(5), "Missing start")).when().post(BASE_PATH);

        assertEquals(400, response.getStatusCode());
    }

    @Test
    @TestSecurity(user = USER_EMAIL, roles = "user")
    @JwtSecurity(claims = { @Claim(key = "email", value = USER_EMAIL), @Claim(key = "sub", value = "avail-user") })
    void createPersonal_missingEndDate_isRejected() {
        seedUsers();
        companyA();

        Response response = given().contentType("application/json")
                .body(requestBody(LocalDate.now().plusDays(5), null, "Missing end")).when().post(BASE_PATH);

        assertEquals(400, response.getStatusCode());
    }

    @Test
    @TestSecurity(user = USER_EMAIL, roles = "user")
    @JwtSecurity(claims = { @Claim(key = "email", value = USER_EMAIL), @Claim(key = "sub", value = "avail-user") })
    void createPersonal_reasonWith254Characters_returns201() {
        seedUsers();
        companyA();

        String reason = "a".repeat(254);

        Response response = given().contentType("application/json")
                .body(requestBody(LocalDate.now().plusDays(5), LocalDate.now().plusDays(6), reason)).when()
                .post(BASE_PATH);

        assertEquals(201, response.getStatusCode());
        assertEquals(reason, response.jsonPath().getString("reason"));
    }

    @Test
    @TestSecurity(user = USER_EMAIL, roles = "user")
    @JwtSecurity(claims = { @Claim(key = "email", value = USER_EMAIL), @Claim(key = "sub", value = "avail-user") })
    void createPersonal_reasonWith255Characters_returns201() {
        seedUsers();
        companyA();

        String reason = "a".repeat(255);

        Response response = given().contentType("application/json")
                .body(requestBody(LocalDate.now().plusDays(5), LocalDate.now().plusDays(6), reason)).when()
                .post(BASE_PATH);

        assertEquals(201, response.getStatusCode());
        assertEquals(reason, response.jsonPath().getString("reason"));
    }

    @Test
    @TestSecurity(user = USER_EMAIL, roles = "user")
    @JwtSecurity(claims = { @Claim(key = "email", value = USER_EMAIL), @Claim(key = "sub", value = "avail-user") })
    void createPersonal_reasonExceeding255Characters_returns400() {
        seedUsers();
        Company company = companyA();
        User user = User.find("email", USER_EMAIL).firstResult();

        String reason = "a".repeat(256);

        Response response = given().contentType("application/json")
                .body(requestBody(LocalDate.now().plusDays(5), LocalDate.now().plusDays(6), reason)).when()
                .post(BASE_PATH);

        assertEquals(400, response.getStatusCode());
        assertTrue(service.getForUser(user).isEmpty());
    }

    @Test
    @TestSecurity(user = USER_EMAIL, roles = "user")
    @JwtSecurity(claims = { @Claim(key = "email", value = USER_EMAIL), @Claim(key = "sub", value = "avail-user") })
    void update_nonexistentAvailability_returns404() {
        seedUsers();
        companyA();

        Response response = given().contentType("application/json")
                .body(requestBody(LocalDate.now().plusDays(5), LocalDate.now().plusDays(6), "Not found")).when()
                .put(BASE_PATH + "/999999999");

        assertEquals(404, response.getStatusCode());
    }

    @Test
    @TestSecurity(user = USER_EMAIL, roles = "user")
    @JwtSecurity(claims = { @Claim(key = "email", value = USER_EMAIL), @Claim(key = "sub", value = "avail-user") })
    void delete_nonexistentAvailability_returns404() {
        seedUsers();
        companyA();

        Response response = given().when().delete(BASE_PATH + "/999999999");

        assertEquals(404, response.getStatusCode());
    }

    @Test
    @TestSecurity(user = USER_EMAIL, roles = "user")
    @JwtSecurity(claims = { @Claim(key = "email", value = USER_EMAIL), @Claim(key = "sub", value = "avail-user") })
    void createPersonal_sameStartAndEndDate_isAllowed() {
        seedUsers();
        companyA();
        LocalDate day = LocalDate.now().plusDays(3);

        Response response = given().contentType("application/json").body(requestBody(day, day, "Single day off")).when()
                .post(BASE_PATH);

        response.then().statusCode(201);
    }

    @Test
    @TestSecurity(user = USER_EMAIL, roles = "user")
    @JwtSecurity(claims = { @Claim(key = "email", value = USER_EMAIL), @Claim(key = "sub", value = "avail-user") })
    void user_canGetOwnAvailability_returnsOwnRecordsOnly() {
        seedUsers();
        Company company = companyA();
        User owner = User.find("email", USER_EMAIL).firstResult();
        User other = User.find("email", USER2_EMAIL).firstResult();

        UserAvailability own = createPersonalAvailabilityDirect(owner, company, LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(12), "Own");
        createPersonalAvailabilityDirect(other, company, LocalDate.now().plusDays(20), LocalDate.now().plusDays(22),
                "Other");

        Response response = given().when().get(BASE_PATH + "/me");

        response.then().statusCode(200);
        assertEquals(List.of(own.id), response.jsonPath().getList("id", Long.class));
    }

    @Test
    @TestSecurity(user = USER_EMAIL, roles = "user")
    @JwtSecurity(claims = { @Claim(key = "email", value = USER_EMAIL), @Claim(key = "sub", value = "avail-user") })
    void user_canGetUserAvailability_forOwnUser() {
        seedUsers();
        Company company = companyA();
        User user = User.find("email", USER_EMAIL).firstResult();

        UserAvailability own = createPersonalAvailabilityDirect(user, company, LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(12), "Own");

        Response response = given().when().get(BASE_PATH + "/user/" + user.id);

        response.then().statusCode(200);

        assertEquals(List.of(own.id), response.jsonPath().getList("id", Long.class));
    }

    @Test
    @TestSecurity(user = USER_EMAIL, roles = "user")
    @JwtSecurity(claims = { @Claim(key = "email", value = USER_EMAIL), @Claim(key = "sub", value = "avail-user") })
    void user_cannotGetAnotherUsersAvailability() {
        seedUsers();
        companyA();
        User other = User.find("email", USER2_EMAIL).firstResult();

        createPersonalAvailabilityDirect(other, companyA(), LocalDate.now().plusDays(10), LocalDate.now().plusDays(12),
                "Other");

        given().when().get(BASE_PATH + "/user/" + other.id).then().statusCode(403);
    }

    @Test
    @TestSecurity(user = TAM_EMAIL, roles = "tam")
    @JwtSecurity(claims = { @Claim(key = "email", value = TAM_EMAIL), @Claim(key = "sub", value = "avail-tam") })
    void tam_cannotGetAnotherUsersAvailability() {
        seedUsers();
        companyA();
        User other = User.find("email", USER_EMAIL).firstResult();

        createPersonalAvailabilityDirect(other, companyA(), LocalDate.now().plusDays(10), LocalDate.now().plusDays(12),
                "Other");

        given().when().get(BASE_PATH + "/user/" + other.id).then().statusCode(403);
    }

    @Test
    @TestSecurity(user = SUPPORT_EMAIL, roles = "support")
    @JwtSecurity(claims = { @Claim(key = "email", value = SUPPORT_EMAIL),
            @Claim(key = "sub", value = "avail-support") })
    void support_canGetAnotherUsersAvailability() {
        seedUsers();
        companyA();
        User other = User.find("email", USER_EMAIL).firstResult();

        UserAvailability otherAvailability = createPersonalAvailabilityDirect(other, companyA(),
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(12), "Other PTO");

        Response response = given().when().get(BASE_PATH + "/user/" + other.id);

        response.then().statusCode(200);

        assertEquals(List.of(otherAvailability.id), response.jsonPath().getList("id", Long.class));
    }

    @Test
    @TestSecurity(user = SUPERUSER_EMAIL, roles = "superuser")
    @JwtSecurity(claims = { @Claim(key = "email", value = SUPERUSER_EMAIL),
            @Claim(key = "sub", value = "avail-superuser") })
    void superuser_canGetAnotherUsersAvailability() {
        seedUsers();
        companyA();
        User other = User.find("email", USER_EMAIL).firstResult();

        UserAvailability otherAvailability = createPersonalAvailabilityDirect(other, companyA(),
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(12), "Other PTO");

        Response response = given().when().get(BASE_PATH + "/user/" + other.id);

        response.then().statusCode(200);

        assertEquals(List.of(otherAvailability.id), response.jsonPath().getList("id", Long.class));
    }

    @Test
    @TestSecurity(user = USER_EMAIL, roles = "user")
    @JwtSecurity(claims = { @Claim(key = "email", value = USER_EMAIL), @Claim(key = "sub", value = "avail-user") })
    void getUserAvailability_forUnknownUser_returnsNotFound() {
        seedUsers();
        companyA();

        given().when().get(BASE_PATH + "/user/999999999").then().statusCode(404);
    }

    @Test
    void getUserAvailability_withoutAuthentication_returnsUnauthorized() {
        given().when().get(BASE_PATH + "/user/1").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = SUPPORT_EMAIL, roles = "support")
    @JwtSecurity(claims = { @Claim(key = "email", value = SUPPORT_EMAIL),
            @Claim(key = "sub", value = "avail-support") })
    void support_canGetAnotherCompanyUsersAvailability() {
        seedUsers();
        companyA();
        companyB();
        User other = User.find("email", USER_B_EMAIL).firstResult();

        UserAvailability otherAvailability = createPersonalAvailabilityDirect(other, companyB(),
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(12), "Other PTO");

        Response response = given().when().get(BASE_PATH + "/user/" + other.id);

        response.then().statusCode(200);

        assertEquals(List.of(otherAvailability.id), response.jsonPath().getList("id", Long.class));
    }

    @Test
    @TestSecurity(user = SUPERUSER_EMAIL, roles = "superuser")
    @JwtSecurity(claims = { @Claim(key = "email", value = SUPERUSER_EMAIL),
            @Claim(key = "sub", value = "avail-superuser") })
    void superuser_canGetAnotherCompanyUsersAvailability() {
        seedUsers();
        companyA();
        companyB();
        User other = User.find("email", USER_B_EMAIL).firstResult();

        UserAvailability otherAvailability = createPersonalAvailabilityDirect(other, companyB(),
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(12), "Other PTO");

        Response response = given().when().get(BASE_PATH + "/user/" + other.id);

        response.then().statusCode(200);

        assertEquals(List.of(otherAvailability.id), response.jsonPath().getList("id", Long.class));
    }

    @Test
    @TestSecurity(user = TAM_EMAIL, roles = "tam")
    @JwtSecurity(claims = { @Claim(key = "email", value = TAM_EMAIL), @Claim(key = "sub", value = "avail-tam") })
    void tam_cannotGetAnotherCompanyUsersAvailability() {
        seedUsers();
        companyA();
        companyB();
        User other = User.find("email", USER_B_EMAIL).firstResult();

        createPersonalAvailabilityDirect(other, companyB(), LocalDate.now().plusDays(10), LocalDate.now().plusDays(12),
                "Other PTO");

        given().when().get(BASE_PATH + "/user/" + other.id).then().statusCode(403);
    }

    @Test
    @TestSecurity(user = TAM_EMAIL, roles = "tam")
    @JwtSecurity(claims = { @Claim(key = "email", value = TAM_EMAIL), @Claim(key = "sub", value = "avail-tam") })
    void tam_canManageOwnAvailability() {
        seedUsers();
        companyA();

        Response create = given().contentType("application/json")
                .body(requestBody(LocalDate.now().plusDays(10), LocalDate.now().plusDays(15), "TAM PTO")).when()
                .post(BASE_PATH);
        create.then().statusCode(201);
        Long id = create.jsonPath().getLong("id");

        given().when().get(BASE_PATH + "/me").then().statusCode(200).body("id", hasItem(id.intValue()));

        given().contentType("application/json")
                .body(requestBody(LocalDate.now().plusDays(12), LocalDate.now().plusDays(16), "TAM PTO extended"))
                .when().put(BASE_PATH + "/" + id).then().statusCode(200);

        given().when().delete(BASE_PATH + "/" + id).then().statusCode(204);
        assertEquals(null, UserAvailability.findById(id));
    }

    @Test
    @TestSecurity(user = TAM2_EMAIL, roles = "tam")
    @JwtSecurity(claims = { @Claim(key = "email", value = TAM2_EMAIL), @Claim(key = "sub", value = "avail-tam-2") })
    void tam_updateAnotherUsersAvailability_isForbidden() {
        seedUsers();
        Company company = companyA();
        User owner = User.find("email", TAM_EMAIL).firstResult();

        UserAvailability availability = createPersonalAvailabilityDirect(owner, company, LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(15), "Owner PTO");

        given().contentType("application/json")
                .body(requestBody(LocalDate.now().plusDays(11), LocalDate.now().plusDays(16), "Hijacked")).when()
                .put(BASE_PATH + "/" + availability.id).then().statusCode(401);

        UserAvailability reloaded = UserAvailability.findById(availability.id);
        assertNotNull(reloaded);
        assertEquals(LocalDate.now().plusDays(10), reloaded.startDate);
    }

    @Test
    @TestSecurity(user = USER_EMAIL, roles = "user")
    @JwtSecurity(claims = { @Claim(key = "email", value = USER_EMAIL), @Claim(key = "sub", value = "avail-user") })
    void user_canViewCompanyAvailability() {
        seedUsers();
        companyA();

        given().when().get(BASE_PATH + "/company").then().statusCode(200);
    }

    @Test
    @TestSecurity(user = TAM2_EMAIL, roles = "tam")
    @JwtSecurity(claims = { @Claim(key = "email", value = TAM2_EMAIL), @Claim(key = "sub", value = "avail-tam-2") })
    void tam_deleteAnotherUsersAvailability_isForbidden() {
        seedUsers();
        Company company = companyA();
        User owner = User.find("email", TAM_EMAIL).firstResult();

        UserAvailability availability = createPersonalAvailabilityDirect(owner, company, LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(15), "Owner PTO");

        given().when().delete(BASE_PATH + "/" + availability.id).then().statusCode(401);

        assertNotNull(UserAvailability.findById(availability.id));
    }

    @Test
    @TestSecurity(user = TAM_EMAIL, roles = "tam")
    @JwtSecurity(claims = { @Claim(key = "email", value = TAM_EMAIL), @Claim(key = "sub", value = "avail-tam") })
    void tam_canViewCompanyAvailability() {
        seedUsers();
        companyA();

        given().when().get(BASE_PATH + "/company").then().statusCode(200);
    }

    @Test
    @TestSecurity(user = TAM_EMAIL, roles = "tam")
    @JwtSecurity(claims = { @Claim(key = "email", value = TAM_EMAIL), @Claim(key = "sub", value = "avail-tam") })
    void tam_companyWidePost_isForbidden() {
        seedUsers();
        Company company = companyA();

        Response response = given().contentType("application/json")
                .body(requestBody(LocalDate.now().plusDays(1), LocalDate.now().plusDays(2), "Company retreat")).when()
                .post(BASE_PATH + "/company");

        response.then().statusCode(401);
        assertTrue(service.getForCompany(company).isEmpty());
    }

    @Test
    @TestSecurity(user = SUPPORT_EMAIL, roles = "support")
    @JwtSecurity(claims = { @Claim(key = "email", value = SUPPORT_EMAIL),
            @Claim(key = "sub", value = "avail-support") })
    void support_canManageOwnAvailability() {
        seedUsers();
        companyA();

        Response create = given().contentType("application/json")
                .body(requestBody(LocalDate.now().plusDays(10), LocalDate.now().plusDays(15), "Support PTO")).when()
                .post(BASE_PATH);
        create.then().statusCode(201);
        Long id = create.jsonPath().getLong("id");

        given().when().get(BASE_PATH + "/me").then().statusCode(200).body("id", hasItem(id.intValue()));

        given().when().delete(BASE_PATH + "/" + id).then().statusCode(204);
    }

    @Test
    @TestSecurity(user = SUPPORT_EMAIL, roles = "support")
    @JwtSecurity(claims = { @Claim(key = "email", value = SUPPORT_EMAIL),
            @Claim(key = "sub", value = "avail-support") })
    void support_cannotUpdateAnotherUsersPersonalAvailability() {
        seedUsers();
        Company company = companyA();
        User owner = User.find("email", USER_EMAIL).firstResult();

        UserAvailability availability = createPersonalAvailabilityDirect(owner, company, LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(15), "Owner PTO");

        given().contentType("application/json")
                .body(requestBody(LocalDate.now().plusDays(11), LocalDate.now().plusDays(16), "Hijacked")).when()
                .put(BASE_PATH + "/" + availability.id).then().statusCode(401);

        UserAvailability reloaded = UserAvailability.findById(availability.id);
        assertEquals(LocalDate.now().plusDays(10), reloaded.startDate);
    }

    @Test
    @TestSecurity(user = SUPPORT_EMAIL, roles = "support")
    @JwtSecurity(claims = { @Claim(key = "email", value = SUPPORT_EMAIL),
            @Claim(key = "sub", value = "avail-support") })
    void support_companyWideGet_returnsOnlyOwnCompany() {
        seedUsers();
        Company companyA = companyA();
        Company companyB = companyB();
        User supportA = User.find("email", SUPPORT_EMAIL).firstResult();
        User supportB = User.find("email", SUPPORT2_EMAIL).firstResult();

        UserAvailability ownCompany = createCompanyAvailabilityDirect(supportA, companyA, LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(15), "Company A retreat");
        createCompanyAvailabilityDirect(supportB, companyB, LocalDate.now().plusDays(10), LocalDate.now().plusDays(15),
                "Company B retreat");

        Response response = given().when().get(BASE_PATH + "/company");

        response.then().statusCode(200);
        assertEquals(List.of(ownCompany.id), response.jsonPath().getList("id", Long.class));
    }

    @Test
    @TestSecurity(user = SUPPORT_EMAIL, roles = "support")
    @JwtSecurity(claims = { @Claim(key = "email", value = SUPPORT_EMAIL),
            @Claim(key = "sub", value = "avail-support") })
    void support_cannotManageAnotherCompanysCompanyAvailability() {
        seedUsers();
        companyA();
        Company companyB = companyB();
        User supportB = User.find("email", SUPPORT2_EMAIL).firstResult();

        UserAvailability companyAvailabilityB = createCompanyAvailabilityDirect(supportB, companyB,
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(15), "Company B retreat");

        given().contentType("application/json")
                .body(requestBody(LocalDate.now().plusDays(11), LocalDate.now().plusDays(16), "Hijacked")).when()
                .put(BASE_PATH + "/" + companyAvailabilityB.id).then().statusCode(401);

        given().when().delete(BASE_PATH + "/" + companyAvailabilityB.id).then().statusCode(401);

        assertNotNull(UserAvailability.findById(companyAvailabilityB.id));
    }

    @Test
    @TestSecurity(user = SUPERUSER_EMAIL, roles = "superuser")
    @JwtSecurity(claims = { @Claim(key = "email", value = SUPERUSER_EMAIL),
            @Claim(key = "sub", value = "avail-superuser") })
    void superuser_canManageOwnAvailability() {
        seedUsers();
        companyA();

        Response create = given().contentType("application/json")
                .body(requestBody(LocalDate.now().plusDays(10), LocalDate.now().plusDays(15), "Superuser PTO")).when()
                .post(BASE_PATH);
        create.then().statusCode(201);
        Long id = create.jsonPath().getLong("id");

        given().when().get(BASE_PATH + "/me").then().statusCode(200).body("id", hasItem(id.intValue()));

        given().when().delete(BASE_PATH + "/" + id).then().statusCode(204);
    }

    @Test
    @TestSecurity(user = SUPERUSER_EMAIL, roles = "superuser")
    @JwtSecurity(claims = { @Claim(key = "email", value = SUPERUSER_EMAIL),
            @Claim(key = "sub", value = "avail-superuser") })
    void superuser_companyWideCreateAndGetForOwnCompany_isAllowed() {
        seedUsers();
        Company companyB = companyB();
        User superuserB = User.find("email", SUPERUSER2_EMAIL).firstResult();

        Response create = given().contentType("application/json")
                .body(requestBody(LocalDate.now().plusDays(10), LocalDate.now().plusDays(15), "Company A shutdown"))
                .when().post(BASE_PATH + "/company");
        create.then().statusCode(201);
        Long ownCompanyId = create.jsonPath().getLong("id");

        createCompanyAvailabilityDirect(superuserB, companyB, LocalDate.now().plusDays(20),
                LocalDate.now().plusDays(25), "Company B shutdown");

        Response response = given().when().get(BASE_PATH + "/company");

        response.then().statusCode(200);
        assertEquals(List.of(ownCompanyId), response.jsonPath().getList("id", Long.class));
    }

    @Test
    @TestSecurity(user = SUPERUSER_EMAIL, roles = "superuser")
    @JwtSecurity(claims = { @Claim(key = "email", value = SUPERUSER_EMAIL),
            @Claim(key = "sub", value = "avail-superuser") })
    void superuser_cannotManageAnotherCompanysCompanyAvailability() {
        seedUsers();
        companyA();
        Company companyB = companyB();
        User superuserB = User.find("email", SUPERUSER2_EMAIL).firstResult();

        UserAvailability companyAvailabilityB = createCompanyAvailabilityDirect(superuserB, companyB,
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(15), "Company B shutdown");

        given().contentType("application/json")
                .body(requestBody(LocalDate.now().plusDays(11), LocalDate.now().plusDays(16), "Hijacked")).when()
                .put(BASE_PATH + "/" + companyAvailabilityB.id).then().statusCode(401);

        given().when().delete(BASE_PATH + "/" + companyAvailabilityB.id).then().statusCode(401);

        assertNotNull(UserAvailability.findById(companyAvailabilityB.id));
    }

    @Test
    void unauthenticated_companyGet_returnsUnauthorized() {
        given().when().get(BASE_PATH + "/company").then().statusCode(401);
    }

    @Test
    void unauthenticated_companyPost_returnsUnauthorized() {
        given().contentType("application/json").body(requestBody(LocalDate.now(), LocalDate.now().plusDays(1), "PTO"))
                .when().post(BASE_PATH + "/company").then().statusCode(401);
    }

    @Test
    void unauthenticated_update_returnsUnauthorized() {
        given().contentType("application/json").body(requestBody(LocalDate.now(), LocalDate.now().plusDays(1), "PTO"))
                .when().put(BASE_PATH + "/1").then().statusCode(401);
    }

    @Test
    void unauthenticated_delete_returnsUnauthorized() {
        given().when().delete(BASE_PATH + "/1").then().statusCode(401);
    }

    @Test
    void getWarningsForTicket_reportsParticipantsAndCompanyOutOfOffice() {
        seedUsers();
        Company company = companyA();
        User support = User.find("email", SUPPORT_EMAIL).firstResult();
        User tam = User.find("email", TAM_EMAIL).firstResult();
        User regularUser = User.find("email", USER_EMAIL).firstResult();

        Ticket ticket = createTicketWithParticipants(company, support, tam, regularUser);

        LocalDate today = LocalDate.now();
        createPersonalAvailabilityDirect(regularUser, company, today, today.plusDays(2), "Personal PTO");
        createCompanyAvailabilityDirect(support, company, today, today.plusDays(2), "Company retreat");

        List<String> warnings = new ArrayList<>();
        QuarkusTransaction.requiringNew()
                .run(() -> warnings.addAll(service.getWarningsForTicket(Ticket.findById(ticket.id))));

        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("Personal PTO")),
                "Personal availability of a ticket user should produce a warning: " + warnings);
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("Company retreat")),
                "Company-wide availability should produce a warning: " + warnings);
    }

    @Test
    void getWarningsForTicket_withoutActiveAvailability_returnsNoWarnings() {
        seedUsers();
        Company company = companyA();
        User support = User.find("email", SUPPORT_EMAIL).firstResult();
        User tam = User.find("email", TAM_EMAIL).firstResult();
        User regularUser = User.find("email", USER_EMAIL).firstResult();

        Ticket ticket = createTicketWithParticipants(company, support, tam, regularUser);

        LocalDate today = LocalDate.now();
        createPersonalAvailabilityDirect(regularUser, company, today.plusDays(10), today.plusDays(12), "Future PTO");

        List<String> warnings = new ArrayList<>();
        QuarkusTransaction.requiringNew()
                .run(() -> warnings.addAll(service.getWarningsForTicket(Ticket.findById(ticket.id))));

        assertTrue(warnings.isEmpty(), "Future availability should not produce warnings: " + warnings);
    }
}
