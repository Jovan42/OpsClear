<#-- "Check your inbox" page for the VERIFY_EMAIL required action (ADR-0059). Keycloak sends
     the verification email when this page first shows; url.loginAction sends it again. -->
<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true; section>
    <#if section = "header">
        ${msg("emailVerifyTitle")}
    <#elseif section = "form">
        <div id="kc-verify-email">
            <p class="instruction">${msg("emailVerifySentTo")}</p>
            <p id="kc-verify-email-address" class="oc-verify-email-address">${(user.email)!''}</p>
            <p class="instruction">${msg("emailVerifyNextStep")}</p>
        </div>
    <#elseif section = "info">
        <p class="instruction">
            ${msg("emailVerifyNotReceived")}
            <a id="kc-verify-email-resend" href="${url.loginAction}">${msg("emailVerifyResend")}</a>
        </p>
        <p><a id="kc-verify-email-back" href="${url.loginUrl}">${kcSanitize(msg("backToLogin"))?no_esc}</a></p>
    </#if>
</@layout.registrationLayout>
