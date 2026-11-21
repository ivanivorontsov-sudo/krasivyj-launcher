package org.ivanivorontsov.launcher.ai

/**
 * Web-chat drivers used the same way as in the user's keyboard «ИИ-фон»:
 * the user signs into Qwen / ChatGPT / Gemini / Grok / Alice / Copilot once,
 * cookies stay on the device, and a WebView injects a prompt and reads the
 * reply. There is no paid API key.
 *
 * The JavaScript here is written for this launcher (text plans, not images).
 * Pollinations text (also used by the keyboard, free, no login) is the
 * no-account option.
 */
object ChatEngine {
    data class Source(val id: String, val title: String, val url: String?, val needsLogin: Boolean)

    val SOURCES: List<Source> = listOf(
        Source("local", "Только на телефоне (без сети)", null, false),
        Source("pollinations", "Pollinations (бесплатно, без входа)", null, false),
        Source("qwen", "Qwen (ваш аккаунт)", "https://chat.qwen.ai/", true),
        Source("chatgpt", "ChatGPT (ваш аккаунт)", "https://chatgpt.com/", true),
        Source("gemini", "Gemini (ваш аккаунт)", "https://gemini.google.com/app", true),
        Source("grok", "Grok (ваш аккаунт)", "https://grok.com/", true),
        Source("alice", "Алиса (ваш аккаунт)", "https://alice.yandex.ru/", true),
        Source("copilot", "Copilot (ваш аккаунт)", "https://copilot.microsoft.com/", true),
    )

    private const val HELPERS = """
        function uyVis(e){if(!e||!e.getBoundingClientRect)return false;var r=e.getBoundingClientRect();
          var s=window.getComputedStyle?getComputedStyle(e):null;return r.width>0&&(r.height>0||e.isContentEditable)&&(!s||(s.visibility!=='hidden'&&s.display!=='none'));}
        function uyInput(){var sels=['#prompt-textarea','div.ProseMirror[contenteditable=true]','textarea#chat-input','#chat-input',
          'rich-textarea .ql-editor','div.ql-editor[contenteditable=true]','textarea[placeholder]','textarea',
          '[contenteditable=true][role=textbox]','[contenteditable=true]','[contenteditable=plaintext-only]'];
          for(var i=0;i<sels.length;i++){var l=document.querySelectorAll(sels[i]);for(var j=0;j<l.length;j++)if(uyVis(l[j]))return l[j];}
          return null;}
        function uyText(el){return el?((el.value!==undefined&&el.tagName!=='DIV')?el.value:(el.innerText||el.textContent||'')):'';}
    """

    const val STATE = """(function(){$HELPERS
        var pw=document.querySelector('input[type=password]');return ((pw&&uyVis(pw))||!uyInput())?'login':'ok';})()"""

    fun inject(prompt: String): String {
        val p = jsonQuote(prompt)
        return """(function(){$HELPERS
            var p=$p;var el=uyInput();if(!el)return 'noinput';
            el.focus();
            if(el.tagName==='TEXTAREA'||el.tagName==='INPUT'){
              var proto=el.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;
              var setter=Object.getOwnPropertyDescriptor(proto,'value').set;setter.call(el,p);
              try{el.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:p}));}catch(e){el.dispatchEvent(new Event('input',{bubbles:true}));}
              el.dispatchEvent(new Event('change',{bubbles:true}));
            }else{
              try{var sel=window.getSelection();var r=document.createRange();r.selectNodeContents(el);sel.removeAllRanges();sel.addRange(r);}catch(e){}
              var ok=false;try{ok=document.execCommand('insertText',false,p);}catch(e){}
              if(!ok||uyText(el).indexOf(p.slice(0,16))<0){
                try{el.dispatchEvent(new InputEvent('beforeinput',{bubbles:true,cancelable:true,inputType:'insertText',data:p}));}catch(e){}
                if(uyText(el).indexOf(p.slice(0,16))<0){while(el.firstChild)el.removeChild(el.firstChild);var para=document.createElement('p');para.textContent=p;el.appendChild(para);}
                try{el.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:p}));}catch(e){el.dispatchEvent(new Event('input',{bubbles:true}));}
              }
            }
            window.__uyEl=el;window.__uyPrompt=p;window.__uyTries=0;window.__uyLen=0;window.__uyStable=0;
            return uyText(el).indexOf(p.slice(0,16))>=0?'ok':'typed?';})()"""
    }

    const val SUBMIT = """(function(){$HELPERS
        var el=window.__uyEl||uyInput();var p=window.__uyPrompt||'';
        if(!el)return 'noinput';var cur=uyInput()||el;
        if(window.__uyTries>0&&(!cur||uyText(cur).indexOf(p.slice(0,16))<0))return 'sent';
        var t=window.__uyTries=(window.__uyTries||0)+1;if(t>8)return 'failed';
        function usable(b){return uyVis(b)&&!b.disabled&&b.getAttribute('aria-disabled')!=='true';}
        var sels=['button[data-testid=send-button]','#composer-submit-button','#send-message-button','button[aria-label*=Send i]',
          'button[aria-label*=Отправ i]','button[aria-label*=Submit i]','button.send-button','button[class*=send i]','[data-testid*=send i]',
          'button[type=submit]','[role=button][aria-label*=Send i]','[role=button][aria-label*=Отправ i]'];
        if(t<=4){for(var i=0;i<sels.length;i++){var l=document.querySelectorAll(sels[i]);for(var j=0;j<l.length;j++)if(usable(l[j])){l[j].click();return 'clicked';}}}
        if(t===2||t===5){var c=el;for(var k=0;k<5&&c;k++){c=c.parentElement;if(!c)break;
          var bs=Array.prototype.filter.call(c.querySelectorAll('button'),function(b){return usable(b)&&b.querySelector('svg');});
          if(bs.length){bs[bs.length-1].click();return 'clicked-near';}}}
        if(el){el.focus();var o={key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true};
          el.dispatchEvent(new KeyboardEvent('keydown',o));el.dispatchEvent(new KeyboardEvent('keypress',o));el.dispatchEvent(new KeyboardEvent('keyup',o));}
        if(t>=6){var f=el&&el.closest?el.closest('form'):null;if(f){try{if(f.requestSubmit)f.requestSubmit();else f.submit();return 'form';}catch(e){}}}
        return 'enter';})()"""

    /**
     * Returns JSON {state, text, stable}. [text] is the longest assistant-looking block
     * that is not the prompt itself. [stable] is true once the text length has not grown
     * for a few polls, so the caller can stop waiting.
     */
    const val READ_REPLY = """(function(){$HELPERS
        var p=window.__uyPrompt||'';
        var sels=['[data-message-author-role=assistant]','[data-testid*=assistant]','[class*=assistant] .markdown',
          '.markdown','article','[class*=message]','[class*=answer]','[class*=response]','[class*=bubble]'];
        var best='';var seen={};
        function consider(t){t=(t||'').trim();if(t.length<8)return;if(p&&t.indexOf(p.slice(0,40))===0)return;
          if(t.length>best.length)best=t;}
        for(var i=0;i<sels.length;i++){var l=document.querySelectorAll(sels[i]);
          for(var j=0;j<l.length;j++){if(!uyVis(l[j]))continue;consider(uyText(l[j]));}}
        if(!best){var nodes=document.querySelectorAll('p,li,pre,code,div');
          for(var k=0;k<nodes.length;k++){var t=uyText(nodes[k]);if(t.indexOf('{')>=0)consider(t);}}
        var len=best.length;if(len>0&&len===window.__uyLen)window.__uyStable=(window.__uyStable||0)+1;else window.__uyStable=0;
        window.__uyLen=len;
        var pw=document.querySelector('input[type=password]');
        var state=((pw&&uyVis(pw))||!uyInput())&&len===0?'login':'ok';
        return JSON.stringify({state:state,text:best,stable:window.__uyStable>=3,len:len});})()"""

    fun pollinationsUrl(prompt: String): String =
        "https://text.pollinations.ai/" + java.net.URLEncoder.encode(prompt, "UTF-8").replace("+", "%20")

    const val POLLINATIONS_CHAT = "https://text.pollinations.ai/openai"

    fun jsonQuote(s: String): String {
        val sb = StringBuilder(s.length + 8)
        sb.append('"')
        for (ch in s) when (ch) {
            '\\' -> sb.append("\\\\")
            '"' -> sb.append("\\\"")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> if (ch < ' ') sb.append("\\u").append(ch.code.toString(16).padStart(4, '0')) else sb.append(ch)
        }
        sb.append('"')
        return sb.toString()
    }
}
