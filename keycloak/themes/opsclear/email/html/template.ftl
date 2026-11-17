<#-- Light OpsClear layout for every HTML email (ADR-0059, amended 2026-11-17): a white card on a
     light grey background with an "OpsClear" header. Inline styles and tables only, because email
     clients ignore <style> blocks and CSS variables. Colours are the login theme's brand tokens
     (keycloak/themes/opsclear/login/resources/css/theme.css). The base theme's per-email
     templates call this macro with the message body. -->
<#macro emailLayout>
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
</head>
<body style="margin:0; padding:0; background-color:#f3f4f6;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#f3f4f6;">
  <tr>
    <td align="center" style="padding:32px 16px;">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:560px; background-color:#ffffff; border:1px solid #e5e7eb; border-radius:12px;">
        <tr>
          <td style="padding:24px 32px 0; font-family:'Inter',-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif; font-size:18px; font-weight:700; color:#059669;">OpsClear</td>
        </tr>
        <tr>
          <td style="padding:8px 32px 32px; font-family:'Inter',-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif; font-size:15px; line-height:1.6; color:#374151;">
            <#nested>
          </td>
        </tr>
      </table>
    </td>
  </tr>
</table>
</body>
</html>
</#macro>
