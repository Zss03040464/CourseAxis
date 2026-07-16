package dev.shusenzheng64.courseaxis.importer

import org.json.JSONObject

object AcademicLoginScript {
    fun probe(): String = """
        (function() {
          const username = document.querySelector('#yhm');
          const password = document.querySelector('#mm');
          const button = document.querySelector('#dl');
          const captcha = document.querySelector('#yzm');
          const captchaContainer = document.querySelector('#yzmDiv') || (captcha && captcha.parentElement);
          const visible = function(element) {
            if (!element) return false;
            const style = window.getComputedStyle(element);
            return style.display !== 'none' && style.visibility !== 'hidden' && element.getClientRects().length > 0;
          };
          return JSON.stringify({
            loginPage: Boolean(username && password && button),
            captchaVisible: Boolean(captcha && visible(captchaContainer || captcha))
          });
        })()
    """.trimIndent()

    fun fill(credentials: AcademicCredentials, submit: Boolean): String {
        val username = JSONObject.quote(credentials.username)
        val password = JSONObject.quote(credentials.password)
        return """
            (function() {
              const username = document.querySelector('#yhm');
              const password = document.querySelector('#mm');
              const captcha = document.querySelector('#yzm');
              const button = document.querySelector('#dl');
              if (!username || !password) return JSON.stringify({ok:false, reason:'fields-missing'});
              const assign = function(field, value) {
                field.focus();
                field.value = value;
                field.dispatchEvent(new Event('input', {bubbles:true}));
                field.dispatchEvent(new Event('change', {bubbles:true}));
              };
              assign(username, $username);
              assign(password, $password);
              if (${if (submit) "true" else "false"}) {
                if (!button) return JSON.stringify({ok:false, reason:'button-missing'});
                button.click();
                return JSON.stringify({ok:true, submitted:true});
              }
              if (captcha) captcha.focus();
              return JSON.stringify({ok:true, submitted:false});
            })()
        """.trimIndent()
    }
}
