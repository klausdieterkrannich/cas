package org.apereo.cas.ticket.registry;

import org.apereo.cas.ticket.AuthenticationAwareTicket;
import org.apereo.cas.ticket.Ticket;
import org.apereo.cas.ticket.TicketGrantingTicket;
import org.apereo.cas.ticket.TicketGrantingTicketAwareTicket;

import lombok.val;
import org.jooq.lambda.Unchecked;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Interface for a registry that stores tickets. The underlying registry can be
 * backed by anything from a normal HashMap to JGroups for having distributed
 * registries. It is up to specific implementations to determine their clean up
 * strategy. Strategies can include a manual clean up by a registry cleaner or a
 * more sophisticated strategy such as LRU.
 *
 * @author Scott Battaglia
 * @since 3.0.0
 */
public interface TicketRegistry {

    /**
     * Default bean name.
     */
    String BEAN_NAME = "ticketRegistry";

    /**
     * Add a ticket to the registry. Ticket storage is based on the ticket id.
     *
     * @param ticket The ticket we wish to add to the cache.
     * @throws Exception the exception
     */
    void addTicket(Ticket ticket) throws Exception;

    /**
     * Save.
     *
     * @param toSave the to save
     * @throws Exception the exception
     */
    default void addTicket(final Stream<? extends Ticket> toSave) throws Exception {
        toSave.forEach(Unchecked.consumer(this::addTicket));
    }

    /**
     * Retrieve a ticket from the registry. If the ticket retrieved does not
     * match the expected class, an InvalidTicketException is thrown.
     *
     * @param <T>      the generic ticket type to return that extends {@link Ticket}
     * @param ticketId the id of the ticket we wish to retrieve.
     * @param clazz    The expected class of the ticket we wish to retrieve.
     * @return the requested ticket.
     */
    <T extends Ticket> T getTicket(String ticketId, Class<T> clazz);

    /**
     * Retrieve a ticket from the registry.
     *
     * @param ticketId the id of the ticket we wish to retrieve
     * @return the requested ticket.
     */
    Ticket getTicket(String ticketId);

    /**
     * Gets ticket from registry using a predicate.
     *
     * @param ticketId  the ticket id
     * @param predicate the predicate that tests the ticket
     * @return the ticket
     */
    Ticket getTicket(String ticketId, Predicate<Ticket> predicate);

    /**
     * Remove a specific ticket from the registry.
     * If ticket to delete is TGT then related service tickets are removed as well.
     *
     * @param ticketId The id of the ticket to delete.
     * @return the number of tickets deleted including children.
     * @throws Exception the exception
     */
    int deleteTicket(String ticketId) throws Exception;

    /**
     * Remove a specific ticket from the registry.
     * If ticket to delete is TGT then related service tickets, etc are removed as well.
     *
     * @param ticketId The id of the ticket to delete.
     * @return the number of tickets deleted including children.
     * @throws Exception the exception
     */
    int deleteTicket(Ticket ticketId) throws Exception;

    /**
     * Delete all tickets from the registry.
     *
     * @return the number of tickets deleted.
     */
    long deleteAll();

    /**
     * Retrieve all tickets from the registry.
     *
     * @return collection of tickets currently stored in the registry. Tickets might or might not be valid i.e. expired.
     */
    Collection<? extends Ticket> getTickets();

    /**
     * Gets tickets as a stream having applied a predicate.
     * <p>
     * The returning stream may be bound to an IO channel (such as database connection),
     * so it should be properly closed after usage.
     *
     * @param predicate the predicate
     * @return the tickets
     */
    default Stream<? extends Ticket> getTickets(final Predicate<Ticket> predicate) {
        return stream().filter(predicate);
    }

    /**
     * Update the received ticket.
     *
     * @param ticket the ticket
     * @return the updated ticket
     * @throws Exception the exception
     */
    Ticket updateTicket(Ticket ticket) throws Exception;

    /**
     * Computes the number of SSO sessions stored in the ticket registry.
     *
     * @return Number of ticket-granting tickets in the registry at time of invocation or {@link Integer#MIN_VALUE} if unknown.
     */
    long sessionCount();

    /**
     * Computes the number of service tickets stored in the ticket registry.
     *
     * @return Number of service tickets in the registry at time of invocation or {@link Integer#MIN_VALUE} if unknown.
     */
    long serviceTicketCount();

    /**
     * Gets tickets stream.
     * <p>
     * The returning stream may be bound to an IO channel (such as database connection),
     * so it should be properly closed after usage.
     *
     * @return the tickets stream
     */
    default Stream<? extends Ticket> stream() {
        return getTickets().stream();
    }

    /**
     * Count the number of single sign-on sessions
     * that are recorded in the ticket registry for
     * the given user name.
     *
     * @param principalId the principal id
     * @return the count
     */
    long countSessionsFor(String principalId);

    /**
     * Gets sessions for principal.
     *
     * @param principalId the principal id
     * @return the sessions for
     */
    default Stream<? extends Ticket> getSessionsFor(final String principalId) {
        return getTickets(ticket -> ticket instanceof TicketGrantingTicket
                                    && !ticket.isExpired()
                                    && ((AuthenticationAwareTicket) ticket).getAuthentication().getPrincipal().getId().equals(principalId));
    }

    /**
     * Gets tickets with authenication attributes.
     *
     * @param queryAttributes the query attributes
     * @return the tickets with authenication attributes
     */
    default Stream<? extends Ticket> getSessionsWithAttributes(final Map<String, List<Object>> queryAttributes) {
        return getTickets(ticket -> {
            if (ticket instanceof TicketGrantingTicketAwareTicket ticketGrantingTicket && !ticket.isExpired()) {
                val authentication = ticketGrantingTicket.getAuthentication();
                return queryAttributes.entrySet().stream().anyMatch(entry -> {
                    if (authentication.getAttributes().containsKey(entry.getKey())) {
                        val authnAttributeValues = authentication.getAttributes().get(entry.getKey());
                        return authnAttributeValues.stream().anyMatch(value -> entry.getValue().contains(value));
                    }
                    return false;
                });
            }
            return false;
        });
    }
}
