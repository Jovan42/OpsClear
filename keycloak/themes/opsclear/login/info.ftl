<#-- Branded info page (ADR-0059). Keycloak shows it after a verification link is opened
     outside the original login ("Your email address has been verified") and for other
     action-token results. Same links as the base theme's info.ftl. -->
<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
    <#if section = "header">
        <#if messageHeader??>
            ${messageHeader}
        <#else>
            ${message.summary}
        </#if>
    <#elseif section = "form">
        <div id="kc-info-message">
            <#-- Without a messageHeader the summary is already the page title; repeat it
                 only when there's more to say (the list of required actions). -->
            <#if messageHeader?? || requiredActions??>
                <p class="instruction">${message.summary}<#if requiredActions??><#list requiredActions>: <b><#items as reqActionItem>${kcSanitize(msg("requiredAction.${reqActionItem}"))?no_esc}<#sep>, </#items></b></#list></#if></p>
            </#if>
            <#if !skipLink??>
                <#if pageRedirectUri?has_content>
                    <p><a id="kc-info-link" href="${pageRedirectUri}">${kcSanitize(msg("backToApplication"))?no_esc}</a></p>
                <#elseif actionUri?has_content>
                    <p><a id="kc-info-link" href="${actionUri}">${kcSanitize(msg("proceedWithAction"))?no_esc}</a></p>
                <#elseif (client.baseUrl)?has_content>
                    <p><a id="kc-info-link" href="${client.baseUrl}">${kcSanitize(msg("backToApplication"))?no_esc}</a></p>
                </#if>
            </#if>
        </div>
    </#if>
</@layout.registrationLayout>
