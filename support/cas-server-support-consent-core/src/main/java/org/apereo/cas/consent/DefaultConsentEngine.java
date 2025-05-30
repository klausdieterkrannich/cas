package org.apereo.cas.consent;

import org.apereo.cas.audit.AuditActionResolvers;
import org.apereo.cas.audit.AuditResourceResolvers;
import org.apereo.cas.audit.AuditableActions;
import org.apereo.cas.authentication.Authentication;
import org.apereo.cas.authentication.principal.Service;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.services.RegisteredService;
import org.apereo.cas.services.RegisteredServiceAttributeReleasePolicyContext;
import org.apereo.cas.util.function.FunctionUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apereo.inspektr.audit.annotation.Audit;
import org.springframework.context.ConfigurableApplicationContext;
import java.io.Serial;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This is {@link DefaultConsentEngine}.
 *
 * @author Misagh Moayyed
 * @since 5.1.0
 */
@Slf4j
@RequiredArgsConstructor
@Getter
public class DefaultConsentEngine implements ConsentEngine {
    @Serial
    private static final long serialVersionUID = -617809298856160625L;

    private final ConsentRepository consentRepository;

    private final ConsentDecisionBuilder consentDecisionBuilder;

    private final CasConfigurationProperties casProperties;

    private final List<ConsentableAttributeBuilder> consentableAttributeBuilders;

    private final ConfigurableApplicationContext applicationContext;

    @Audit(action = AuditableActions.SAVE_CONSENT,
        actionResolverName = AuditActionResolvers.SAVE_CONSENT_ACTION_RESOLVER,
        resourceResolverName = AuditResourceResolvers.SAVE_CONSENT_RESOURCE_RESOLVER)
    @Override
    public ConsentDecision storeConsentDecision(final Service service,
                                                final RegisteredService registeredService,
                                                final Authentication authentication,
                                                final long reminder,
                                                final ChronoUnit reminderTimeUnit,
                                                final ConsentReminderOptions options) throws Throwable {
        val attributes = resolveConsentableAttributesFrom(authentication, service, registeredService);
        attributes.replaceAll((key, value) -> {
            var attr = CasConsentableAttribute.builder()
                .name(key)
                .values(value)
                .build();

            for (val builder : this.consentableAttributeBuilders) {
                LOGGER.trace("Preparing to build consentable attribute [{}] via [{}]", attr, builder.getName());
                attr = builder.build(attr);
                LOGGER.trace("Finalized consentable attribute [{}]", attr);
            }
            return attr.getValues();
        });

        val principalId = authentication.getPrincipal().getId();
        val decisionFound = findConsentDecision(service, registeredService, authentication);
        val supplier = FunctionUtils.doIfNull(decisionFound,
            () -> consentDecisionBuilder.build(service, registeredService, principalId, attributes),
            () -> consentDecisionBuilder.update(decisionFound, attributes));

        val decision = supplier.get();
        decision.setOptions(options);
        decision.setReminder(reminder);
        decision.setTenant(service.getTenant());
        decision.setReminderTimeUnit(reminderTimeUnit);
        return consentRepository.storeConsentDecision(decision);
    }

    @Override
    public ConsentDecision findConsentDecision(final Service service,
                                               final RegisteredService registeredService,
                                               final Authentication authentication) {
        return consentRepository.findConsentDecision(service, registeredService, authentication);
    }

    @Override
    public Map<String, List<Object>> resolveConsentableAttributesFrom(
        final Authentication authentication,
        final Service service,
        final RegisteredService registeredService) throws Throwable {
        LOGGER.debug("Retrieving consentable attributes for [{}]", registeredService);
        val policy = registeredService.getAttributeReleasePolicy();
        if (policy != null) {
            val context = RegisteredServiceAttributeReleasePolicyContext.builder()
                .registeredService(registeredService)
                .service(service)
                .principal(authentication.getPrincipal())
                .applicationContext(applicationContext)
                .build();
            val consentableAttributes = policy.getConsentableAttributes(context);
            consentableAttributes.entrySet().removeIf(entry -> {
                val excludedAttributes = casProperties.getConsent().getCore().getExcludedAttributes();
                return excludedAttributes.contains(entry.getKey());
            });
            return consentableAttributes;
        }
        return new LinkedHashMap<>();
    }

    @Override
    public Map<String, List<Object>> resolveConsentableAttributesFrom(final ConsentDecision decision) {
        LOGGER.debug("Retrieving consentable attributes from existing decision made by [{}] for [{}]",
            decision.getPrincipal(), decision.getService());
        return this.consentDecisionBuilder.getConsentableAttributesFrom(decision);
    }

    @Audit(action = AuditableActions.VERIFY_CONSENT,
        actionResolverName = AuditActionResolvers.VERIFY_CONSENT_ACTION_RESOLVER,
        resourceResolverName = AuditResourceResolvers.VERIFY_CONSENT_RESOURCE_RESOLVER)
    @Override
    public ConsentQueryResult isConsentRequiredFor(final Service service,
                                                   final RegisteredService registeredService,
                                                   final Authentication authentication) throws Throwable {
        val attributes = resolveConsentableAttributesFrom(authentication, service, registeredService);
        if (attributes == null || attributes.isEmpty()) {
            LOGGER.debug("Consent is conditionally ignored for service [{}] given no consentable attributes are found", registeredService.getName());
            return ConsentQueryResult.ignored()
                .withService(service).withAuthentication(authentication);
        }

        LOGGER.debug("Locating consent decision for service [{}]", service);
        val decision = findConsentDecision(service, registeredService, authentication);
        if (decision == null) {
            LOGGER.debug("No consent decision found; thus attribute consent is required");
            return ConsentQueryResult.required()
                .withService(service).withAuthentication(authentication);
        }

        LOGGER.debug("Located consentable attributes for release [{}]", attributes.keySet());
        if (consentDecisionBuilder.doesAttributeReleaseRequireConsent(decision, attributes)) {
            LOGGER.debug("Consent is required based on past decision [{}] and attribute release policy for [{}]",
                decision, registeredService.getName());
            return ConsentQueryResult.required().withService(service)
                .withConsentDecision(decision).withAuthentication(authentication);
        }

        LOGGER.debug("Consent is not required yet for [{}]; checking for reminder options", service);
        val unit = decision.getReminderTimeUnit();
        val dt = decision.getCreatedDate().plus(decision.getReminder(), unit);
        val now = LocalDateTime.now(ZoneId.systemDefault());

        LOGGER.debug("Reminder threshold date/time is calculated as [{}]", dt);
        if (now.isAfter(dt)) {
            LOGGER.debug("Consent is required based on reminder options given now at [{}] is after [{}]", now, dt);
            return ConsentQueryResult.required().withService(service)
                .withConsentDecision(decision).withAuthentication(authentication);
        }

        LOGGER.debug("Consent is not required for service [{}]", service);
        return ConsentQueryResult.ignored()
            .withService(service).withAuthentication(authentication);
    }
}
