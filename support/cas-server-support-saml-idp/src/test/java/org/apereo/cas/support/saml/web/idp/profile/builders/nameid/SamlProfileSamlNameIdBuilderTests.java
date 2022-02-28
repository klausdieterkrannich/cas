package org.apereo.cas.support.saml.web.idp.profile.builders.nameid;

import org.apereo.cas.support.saml.BaseSamlIdPConfigurationTests;
import org.apereo.cas.support.saml.services.SamlRegisteredService;
import org.apereo.cas.support.saml.services.idp.metadata.SamlRegisteredServiceServiceProviderMetadataFacade;
import org.apereo.cas.support.saml.web.idp.profile.builders.SamlProfileBuilderContext;
import org.apereo.cas.support.saml.web.idp.profile.builders.SamlProfileObjectBuilder;
import org.apereo.cas.util.CollectionUtils;

import lombok.val;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.AttributeQuery;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.NameID;
import org.opensaml.saml.saml2.core.NameIDPolicy;
import org.opensaml.saml.saml2.core.Subject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * This is {@link SamlProfileSamlNameIdBuilderTests}.
 *
 * @author Misagh Moayyed
 * @since 5.3.0
 */
@Tag("SAML2")
@SuppressWarnings("JavaUtilDate")
public class SamlProfileSamlNameIdBuilderTests extends BaseSamlIdPConfigurationTests {
    @Autowired
    @Qualifier("samlProfileSamlNameIdBuilder")
    private SamlProfileObjectBuilder<SAMLObject> samlProfileSamlNameIdBuilder;

    @Test
    public void verifyNoSupportedFormats() throws Exception {
        val authnRequest = mock(AuthnRequest.class);
        val issuer = mock(Issuer.class);
        when(issuer.getValue()).thenReturn("https://idp.example.org");
        when(authnRequest.getIssuer()).thenReturn(issuer);

        val policy = mock(NameIDPolicy.class);
        when(policy.getFormat()).thenReturn(NameID.EMAIL);
        when(authnRequest.getNameIDPolicy()).thenReturn(policy);

        val service = new SamlRegisteredService();
        service.setServiceId("entity-id");
        service.setNameIdQualifier("https://qualifier.example.org");
        service.setServiceProviderNameIdQualifier("https://sp-qualifier.example.org");
        service.setRequiredNameIdFormat(NameID.UNSPECIFIED);

        val facade = mock(SamlRegisteredServiceServiceProviderMetadataFacade.class);
        when(facade.getEntityId()).thenReturn(service.getServiceId());
        when(facade.getSupportedNameIdFormats()).thenReturn(new ArrayList<>(0));

        val buildContext = SamlProfileBuilderContext.builder()
            .samlRequest(authnRequest)
            .httpRequest(new MockHttpServletRequest())
            .httpResponse(new MockHttpServletResponse())
            .authenticatedAssertion(getAssertion())
            .registeredService(service)
            .adaptor(facade)
            .binding(SAMLConstants.SAML2_POST_BINDING_URI)
            .build();

        val result = samlProfileSamlNameIdBuilder.build(buildContext);
        assertNotNull(result);
    }

    @Test
    public void verifyUnknownSupportedFormats() throws Exception {
        val authnRequest = mock(AuthnRequest.class);
        val issuer = mock(Issuer.class);
        when(issuer.getValue()).thenReturn("https://idp.example.org");
        when(authnRequest.getIssuer()).thenReturn(issuer);

        val policy = mock(NameIDPolicy.class);
        when(policy.getFormat()).thenReturn("badformat");
        when(authnRequest.getNameIDPolicy()).thenReturn(policy);

        val service = new SamlRegisteredService();
        service.setServiceId("entity-id");
        service.setNameIdQualifier("https://qualifier.example.org");
        service.setServiceProviderNameIdQualifier("https://sp-qualifier.example.org");

        val facade = mock(SamlRegisteredServiceServiceProviderMetadataFacade.class);
        when(facade.getEntityId()).thenReturn(service.getServiceId());
        when(facade.getSupportedNameIdFormats()).thenReturn(new ArrayList<>(0));

        val buildContext = SamlProfileBuilderContext.builder()
            .samlRequest(authnRequest)
            .httpRequest(new MockHttpServletRequest())
            .httpResponse(new MockHttpServletResponse())
            .authenticatedAssertion(getAssertion(null, Map.of()))
            .registeredService(service)
            .adaptor(facade)
            .binding(SAMLConstants.SAML2_POST_BINDING_URI)
            .build();

        val result = samlProfileSamlNameIdBuilder.build(buildContext);
        assertNull(result);
    }


    @Test
    public void verifyNameId() throws Exception {
        verifyNameIdByFormat(NameID.EMAIL);
        verifyNameIdByFormat(NameID.TRANSIENT);
        verifyNameIdByFormat(NameID.PERSISTENT);
        verifyNameIdByFormat(NameID.ENTITY);
        verifyNameIdByFormat(NameID.X509_SUBJECT);
        verifyNameIdByFormat(NameID.WIN_DOMAIN_QUALIFIED);
        verifyNameIdByFormat(NameID.KERBEROS);
        verifyNameIdByFormat(NameID.UNSPECIFIED);
    }

    @Test
    public void verifyPersistedNameIdFormat() throws Exception {
        val service = getSamlRegisteredServiceForTestShib();
        service.setRequiredNameIdFormat(NameID.PERSISTENT);

        val authnRequest = getAuthnRequestFor(service);

        val adaptor = SamlRegisteredServiceServiceProviderMetadataFacade.get(samlRegisteredServiceCachingMetadataResolver,
            service, service.getServiceId()).get();
        val buildContext = SamlProfileBuilderContext.builder()
            .samlRequest(authnRequest)
            .httpRequest(new MockHttpServletRequest())
            .httpResponse(new MockHttpServletResponse())
            .authenticatedAssertion(getAssertion())
            .registeredService(service)
            .adaptor(adaptor)
            .binding(SAMLConstants.SAML2_POST_BINDING_URI)
            .build();

        val subject = samlProfileSamlSubjectBuilder.build(buildContext);
        assertNotNull(subject.getNameID());
        assertEquals(NameID.PERSISTENT, subject.getNameID().getFormat());
        assertEquals(adaptor.getEntityId(), subject.getNameID().getSPNameQualifier());
        assertEquals("https://cas.example.org/idp", subject.getNameID().getNameQualifier());
    }

    @Test
    public void verifyAttributeQueryNameID() throws Exception {
        val service = getSamlRegisteredServiceForTestShib();
        service.setRequiredNameIdFormat(NameID.PERSISTENT);

        val aqNameId = mock(NameID.class);
        when(aqNameId.getValue()).thenReturn(UUID.randomUUID().toString());
        when(aqNameId.getFormat()).thenReturn(NameID.UNSPECIFIED);
        when(aqNameId.getSPNameQualifier()).thenReturn(UUID.randomUUID().toString());
        when(aqNameId.getNameQualifier()).thenReturn(UUID.randomUUID().toString());

        val aqSubject = mock(Subject.class);
        when(aqSubject.getNameID()).thenReturn(aqNameId);
        val attributeQuery = mock(AttributeQuery.class);
        when(attributeQuery.getSubject()).thenReturn(aqSubject);

        val adaptor = SamlRegisteredServiceServiceProviderMetadataFacade.get(samlRegisteredServiceCachingMetadataResolver,
            service, service.getServiceId()).get();
        val buildContext = SamlProfileBuilderContext.builder()
            .samlRequest(attributeQuery)
            .httpRequest(new MockHttpServletRequest())
            .httpResponse(new MockHttpServletResponse())
            .authenticatedAssertion(getAssertion())
            .registeredService(service)
            .adaptor(adaptor)
            .binding(SAMLConstants.SAML2_POST_BINDING_URI)
            .build();
        val subject = samlProfileSamlSubjectBuilder.build(buildContext);
        assertNotNull(subject.getNameID());

        assertEquals(aqNameId.getValue(), subject.getNameID().getValue());
        assertEquals(aqNameId.getFormat(), subject.getNameID().getFormat());
        assertEquals(aqNameId.getSPNameQualifier(), subject.getNameID().getSPNameQualifier());
        assertEquals(aqNameId.getNameQualifier(), subject.getNameID().getNameQualifier());
    }

    @Test
    public void verifyPersistedNameIdFormatWithServiceEntityIdOverride() throws Exception {
        val service = getSamlRegisteredServiceForTestShib();
        service.setRequiredNameIdFormat(NameID.PERSISTENT);
        service.setIssuerEntityId(UUID.randomUUID().toString());

        val authnRequest = getAuthnRequestFor(service);

        val adaptor = SamlRegisteredServiceServiceProviderMetadataFacade.get(samlRegisteredServiceCachingMetadataResolver,
            service, service.getServiceId()).get();

        val buildContext = SamlProfileBuilderContext.builder()
            .samlRequest(authnRequest)
            .httpRequest(new MockHttpServletRequest())
            .httpResponse(new MockHttpServletResponse())
            .authenticatedAssertion(getAssertion())
            .registeredService(service)
            .adaptor(adaptor)
            .binding(SAMLConstants.SAML2_POST_BINDING_URI)
            .build();

        val subject = samlProfileSamlSubjectBuilder.build(buildContext);
        assertNotNull(subject.getNameID());
        assertEquals(NameID.PERSISTENT, subject.getNameID().getFormat());
        assertEquals(adaptor.getEntityId(), subject.getNameID().getSPNameQualifier());
        assertEquals(service.getIssuerEntityId(), subject.getNameID().getNameQualifier());
    }


    @Test
    @SuppressWarnings("JavaUtilDate")
    public void verifyEncryptedNameIdFormat() throws Exception {
        val service = getSamlRegisteredServiceForTestShib();
        service.setRequiredNameIdFormat(NameID.ENCRYPTED);
        service.setSkipGeneratingSubjectConfirmationNameId(false);

        val authnRequest = getAuthnRequestFor(service);

        val adaptor = SamlRegisteredServiceServiceProviderMetadataFacade.get(this.samlRegisteredServiceCachingMetadataResolver,
            service, service.getServiceId()).get();

        val request = new MockHttpServletRequest();
        val buildContext = SamlProfileBuilderContext.builder()
            .samlRequest(authnRequest)
            .httpRequest(request)
            .httpResponse(new MockHttpServletResponse())
            .authenticatedAssertion(getAssertion())
            .registeredService(service)
            .adaptor(adaptor)
            .binding(SAMLConstants.SAML2_POST_BINDING_URI)
            .build();
        val subject = samlProfileSamlSubjectBuilder.build(buildContext);
        assertNull(subject.getNameID());
        assertNotNull(subject.getEncryptedID());
        assertFalse(subject.getSubjectConfirmations().isEmpty());
        val subjectConfirmation = subject.getSubjectConfirmations().get(0);
        assertNotNull(subjectConfirmation.getEncryptedID());
        assertNull(subjectConfirmation.getNameID());
        assertNotNull(request.getAttribute(NameID.class.getName()));
    }

    @Test
    public void verifySkipTransient() throws Exception {
        val authnRequest = mock(AuthnRequest.class);
        val issuer = mock(Issuer.class);
        when(issuer.getValue()).thenReturn("https://idp.example.org");
        when(authnRequest.getIssuer()).thenReturn(issuer);

        val policy = mock(NameIDPolicy.class);
        when(policy.getFormat()).thenReturn(NameID.TRANSIENT);
        when(authnRequest.getNameIDPolicy()).thenReturn(policy);

        val service = new SamlRegisteredService();
        service.setServiceId("entity-id");
        service.setSkipGeneratingTransientNameId(true);
        service.setRequiredNameIdFormat(NameID.TRANSIENT);
        val facade = mock(SamlRegisteredServiceServiceProviderMetadataFacade.class);
        when(facade.getEntityId()).thenReturn(service.getServiceId());
        when(facade.getSupportedNameIdFormats()).thenReturn(CollectionUtils.wrapList(NameID.TRANSIENT));

        val buildContext = SamlProfileBuilderContext.builder()
            .samlRequest(authnRequest)
            .httpRequest(new MockHttpServletRequest())
            .httpResponse(new MockHttpServletResponse())
            .authenticatedAssertion(getAssertion())
            .registeredService(service)
            .adaptor(facade)
            .binding(SAMLConstants.SAML2_POST_BINDING_URI)
            .build();
        val result = (NameID) samlProfileSamlNameIdBuilder.build(buildContext);
        assertNotNull(result);
        assertEquals(NameID.TRANSIENT, result.getFormat());
        assertEquals("casuser", result.getValue());
    }

    private void verifyNameIdByFormat(final String format) throws Exception {
        val authnRequest = mock(AuthnRequest.class);
        val issuer = mock(Issuer.class);
        when(issuer.getValue()).thenReturn("https://idp.example.org");
        when(authnRequest.getIssuer()).thenReturn(issuer);

        val policy = mock(NameIDPolicy.class);
        when(policy.getFormat()).thenReturn(format);
        when(authnRequest.getNameIDPolicy()).thenReturn(policy);

        val service = new SamlRegisteredService();
        service.setServiceId("entity-id");
        service.setRequiredNameIdFormat(format);
        val facade = mock(SamlRegisteredServiceServiceProviderMetadataFacade.class);
        when(facade.getEntityId()).thenReturn(service.getServiceId());
        when(facade.getSupportedNameIdFormats()).thenReturn(CollectionUtils.wrapList(NameID.TRANSIENT, format));

        val buildContext = SamlProfileBuilderContext.builder()
            .samlRequest(authnRequest)
            .httpRequest(new MockHttpServletRequest())
            .httpResponse(new MockHttpServletResponse())
            .authenticatedAssertion(getAssertion())
            .registeredService(service)
            .adaptor(facade)
            .binding(SAMLConstants.SAML2_POST_BINDING_URI)
            .build();
        val result = (NameID) samlProfileSamlNameIdBuilder.build(buildContext);
        assertNotNull(result);
        assertEquals(format, result.getFormat());
        if (format.equals(NameID.TRANSIENT)) {
            assertNotEquals("casuser", result.getValue());
        } else {
            assertEquals("casuser", result.getValue());
        }
    }
}
