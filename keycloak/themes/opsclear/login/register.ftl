<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('global') displayRequiredFields=false; section>
    <#if section = "header">
        ${msg("registerTitle")}
    <#elseif section = "form">
        <form id="kc-register-form" action="${url.registrationAction}" method="post">
            <div class="${properties.kcFormGroupClass!}">
                <label for="firstName" class="${properties.kcLabelClass!}">${msg("firstName")}</label>
                <input type="text" id="firstName" class="${properties.kcInputClass!}" name="firstName"
                       value="${(register.formData.firstName!'')}"
                       aria-invalid="<#if messagesPerField.existsError('firstName')>true</#if>"
                />
                <#if messagesPerField.existsError('firstName')>
                    <span id="input-error-firstname" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                        ${kcSanitize(messagesPerField.get('firstName'))?no_esc}
                    </span>
                </#if>
            </div>

            <div class="${properties.kcFormGroupClass!}">
                <label for="lastName" class="${properties.kcLabelClass!}">${msg("lastName")}</label>
                <input type="text" id="lastName" class="${properties.kcInputClass!}" name="lastName"
                       value="${(register.formData.lastName!'')}"
                       aria-invalid="<#if messagesPerField.existsError('lastName')>true</#if>"
                />
                <#if messagesPerField.existsError('lastName')>
                    <span id="input-error-lastname" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                        ${kcSanitize(messagesPerField.get('lastName'))?no_esc}
                    </span>
                </#if>
            </div>

            <div class="${properties.kcFormGroupClass!}">
                <label for="email" class="${properties.kcLabelClass!}">${msg("email")}</label>
                <input type="text" id="email" class="${properties.kcInputClass!}" name="email"
                       value="${(register.formData.email!'')}" autocomplete="email"
                       aria-invalid="<#if messagesPerField.existsError('email')>true</#if>"
                />
                <#if messagesPerField.existsError('email')>
                    <span id="input-error-email" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                        ${kcSanitize(messagesPerField.get('email'))?no_esc}
                    </span>
                </#if>
            </div>

            <#if !realm.registrationEmailAsUsername>
                <div class="${properties.kcFormGroupClass!}">
                    <label for="username" class="${properties.kcLabelClass!}">${msg("username")}</label>
                    <input type="text" id="username" class="${properties.kcInputClass!}" name="username"
                           value="${(register.formData.username!'')}" autocomplete="username"
                           aria-invalid="<#if messagesPerField.existsError('username')>true</#if>"
                    />
                    <#if messagesPerField.existsError('username')>
                        <span id="input-error-username" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                            ${kcSanitize(messagesPerField.get('username'))?no_esc}
                        </span>
                    </#if>
                </div>
            </#if>

            <#if passwordRequired??>
                <div class="${properties.kcFormGroupClass!}">
                    <label for="password" class="${properties.kcLabelClass!}">${msg("password")}</label>
                    <input type="password" id="password" class="${properties.kcInputClass!}" name="password" autocomplete="new-password"
                           aria-invalid="<#if messagesPerField.existsError('password','password-confirm')>true</#if>"
                    />
                    <#if messagesPerField.existsError('password')>
                        <span id="input-error-password" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                            ${kcSanitize(messagesPerField.get('password'))?no_esc}
                        </span>
                    </#if>
                </div>

                <div class="${properties.kcFormGroupClass!}">
                    <label for="password-confirm" class="${properties.kcLabelClass!}">${msg("passwordConfirm")}</label>
                    <input type="password" id="password-confirm" class="${properties.kcInputClass!}" name="password-confirm" autocomplete="new-password"
                           aria-invalid="<#if messagesPerField.existsError('password-confirm')>true</#if>"
                    />
                    <#if messagesPerField.existsError('password-confirm')>
                        <span id="input-error-password-confirm" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                            ${kcSanitize(messagesPerField.get('password-confirm'))?no_esc}
                        </span>
                    </#if>
                </div>
            </#if>

            <#if recaptchaRequired??>
                <div class="${properties.kcFormGroupClass!}">
                    <div class="g-recaptcha" data-size="compact" data-sitekey="${recaptchaSiteKey}"></div>
                </div>
            </#if>

            <div class="${properties.kcFormGroupClass!} ${properties.kcFormSettingClass!}">
                <div id="kc-form-options">
                    <span><a href="${url.loginUrl}">${kcSanitize(msg("backToLogin"))?no_esc}</a></span>
                </div>
            </div>

            <div id="kc-form-buttons" class="${properties.kcFormGroupClass!}">
                <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}" type="submit" value="${msg("doRegister")}"/>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>
