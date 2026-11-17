<#-- Same as the base theme's template, except the body isn't passed through kcSanitize: its
     HTML policy drops the inline styles the button and the raw link need (display, word-break).
     The body is our own message text; the only outside value is Keycloak's link, made safe for
     an attribute below. -->
<#assign safeLink = link?replace('"', '%22')?replace('<', '%3C')?replace('>', '%3E')>
<#import "template.ftl" as layout>
<@layout.emailLayout>
${msg("emailVerificationBodyHtml", safeLink, linkExpiration, realmName, linkExpirationFormatter(linkExpiration))?no_esc}
</@layout.emailLayout>
