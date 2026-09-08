/*
 * Eclipse Public License - v 2.0
 *
 *   THE ACCOMPANYING PROGRAM IS PROVIDED UNDER THE TERMS OF THIS ECLIPSE
 *   PUBLIC LICENSE ("AGREEMENT"). ANY USE, REPRODUCTION OR DISTRIBUTION
 *   OF THE PROGRAM CONSTITUTES RECIPIENT'S ACCEPTANCE OF THIS AGREEMENT.
 */

package ai.mnemosyne_systems.resource;

import ai.mnemosyne_systems.model.Company;
import ai.mnemosyne_systems.model.User;
import ai.mnemosyne_systems.model.UserAvailability;
import ai.mnemosyne_systems.service.UserAvailabilityService;
import ai.mnemosyne_systems.util.AuthHelper;
import ai.mnemosyne_systems.util.CurrentUser;
import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDate;

@Path("/api/availability")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Blocking
@RolesAllowed({ "admin", "support", "superuser", "tam", "user" })
public class UserAvailabilityApiResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    UserAvailabilityService service;

    @GET
    @Path("/me")
    public Response getMyAvailability() {
        User user = currentUser.get();
        return Response.ok(service.getForUser(user).stream().map(this::toResponse).toList()).build();
    }

    @POST
    public Response createPersonal(AvailabilityRequest request) {
        User user = currentUser.get();
        Company company = Company.find("select c from Company c join c.users u where u = ?1", user).firstResult();
        if (company == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("User does not belong to a company").build();
        }
        UserAvailability availability = service.createPersonal(user, company, request.startDate, request.endDate,
                request.reason);
        return Response.status(Response.Status.CREATED).entity(toResponse(availability)).build();
    }

    @GET
    @Path("/user/{userId}")
    public Response getUserAvailability(@PathParam("userId") Long userId) {
        User actor = currentUser.get();
        User targetUser = User.findById(userId);
        if (targetUser == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Support and superusers can view availability for any user, matching
        // the user directories they already have access to. Everyone else can
        // only view their own availability.
        boolean isOwnAvailability = actor.id.equals(targetUser.id);
        if (!isOwnAvailability && !AuthHelper.isSupport(actor) && !AuthHelper.isSuperuser(actor)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        return Response.ok(service.getForUser(targetUser).stream().map(this::toResponse).toList()).build();
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") Long id, AvailabilityRequest request) {
        User user = currentUser.get();
        UserAvailability availability = service.update(user, id, request.startDate, request.endDate, request.reason);
        return Response.ok(toResponse(availability)).build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        User user = currentUser.get();
        service.delete(user, id);
        return Response.noContent().build();
    }

    @GET
    @Path("/company")
    public Response getCompanyAvailability() {
        User user = currentUser.get();
        Company company = Company.find("select c from Company c join c.users u where u = ?1", user).firstResult();
        if (company == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("User does not belong to a company").build();
        }
        return Response.ok(service.getForCompany(company).stream().map(this::toResponse).toList()).build();
    }

    @POST
    @Path("/company")
    public Response createCompany(AvailabilityRequest request) {
        User user = currentUser.get();
        Company company = Company.find("select c from Company c join c.users u where u = ?1", user).firstResult();
        if (company == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("User does not belong to a company").build();
        }
        UserAvailability availability = service.createCompany(user, company, request.startDate, request.endDate,
                request.reason);
        return Response.status(Response.Status.CREATED).entity(toResponse(availability)).build();
    }

    public static class AvailabilityRequest {

        public LocalDate startDate;
        public LocalDate endDate;
        public String reason;
    }

    private AvailabilityResponse toResponse(UserAvailability availability) {
        return new AvailabilityResponse(availability.id, availability.scope, availability.startDate,
                availability.endDate, availability.reason);
    }

    public record AvailabilityResponse(Long id, String scope, LocalDate startDate, LocalDate endDate, String reason) {
    }
}
