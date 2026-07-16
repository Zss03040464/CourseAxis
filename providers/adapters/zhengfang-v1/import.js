(async function courseAxisImport() {
  "use strict";
  const options = window.__COURSEAXIS_OPTIONS__ || {};
  const allowedOrigin = String(options.allowedOrigin || "");
  const pathPrefix = String(options.pathPrefix || "");
  const diagnostics = [];
  const capturedAt = new Date().toISOString();

  function finish(payload) {
    const text = JSON.stringify(payload);
    if (window.CourseAxisBridge && typeof window.CourseAxisBridge.postMessage === "function") {
      window.CourseAxisBridge.postMessage(text);
    } else if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.courseAxis) {
      window.webkit.messageHandlers.courseAxis.postMessage(text);
    } else {
      throw new Error("CourseAxis secure result channel is unavailable");
    }
  }
  function text(value) { return String(value == null ? "" : value).replace(/\u00a0/g, " ").replace(/[\t\r]+/g, " ").trim(); }
  function timesIn(value) {
    const out = [];
    for (const m of text(value).matchAll(/(?:^|\D)([01]?\d|2[0-3])\s*[:：]\s*([0-5]\d)(?!\d)/g)) out.push(String(m[1]).padStart(2,"0")+":"+m[2]);
    return out;
  }
  function addCandidate(results, section, pair, score) {
    section = Number(section);
    if (!Number.isInteger(section) || section < 1 || section > 40 || pair.length < 2 || pair[0] >= pair[1]) return;
    results.push({section, start: pair[0], end: pair[1], score});
  }
  function selectedValue(root, selectors) {
    for (const selector of selectors) {
      const element = root && root.querySelector ? root.querySelector(selector) : null;
      if (!element) continue;
      const value = text(element.value || element.getAttribute("value"));
      if (value) return value;
      const selected = element.querySelector && element.querySelector("option:checked,option[selected]");
      if (selected) return text(selected.value || selected.getAttribute("value") || selected.textContent);
    }
    return "";
  }
  function detectTerm(root) {
    return {
      xnm: selectedValue(root,["#xnm","select[name='xnm']","[data-name='xnm']"]),
      xqm: selectedValue(root,["#xqm","select[name='xqm']","[data-name='xqm']"])
    };
  }
  function extractSectionTimes(root) {
    const results = [];
    if (!root || !root.querySelectorAll) return results;
    for (const row of root.querySelectorAll("tr")) {
      const cells = Array.from(row.querySelectorAll("th,td"));
      const rowText = text(row.innerText || row.textContent);
      const pair = timesIn(rowText);
      let section = null;
      for (const cell of cells) {
        const cellText = text(cell.innerText || cell.textContent);
        let m = cellText.match(/^(?:第\s*)?(\d{1,2})\s*(?:节)?$/);
        if (!m && timesIn(cellText).length >= 2) m = cellText.match(/^\s*(?:第\s*)?(\d{1,2})\s*(?:节)?(?:\s|$)/);
        if (m && Number(m[1]) >= 1 && Number(m[1]) <= 40) { section = Number(m[1]); break; }
      }
      if (section == null) {
        const m = rowText.match(/^\s*(?:第\s*)?(\d{1,2})\s*(?:节)?(?:\s|$)/);
        if (m) section = Number(m[1]);
      }
      addCandidate(results, section, pair, 20);
    }
    for (const element of root.querySelectorAll("[data-section],[data-jc],[data-jcs],[section],[jc],.festival,.jc")) {
      const raw = element.getAttribute("data-section") || element.getAttribute("data-jc") || element.getAttribute("data-jcs") || element.getAttribute("section") || element.getAttribute("jc") || text(element.innerText || element.textContent);
      const m = String(raw).match(/(?:^|\D)(\d{1,2})(?:\D|$)/);
      if (m) addCandidate(results, m[1], timesIn(element.innerText || element.textContent), 10);
    }
    for (const element of root.querySelectorAll("body *")) {
      const own = text(element.innerText || element.textContent);
      const match = own.match(/^(?:第\s*)?(\d{1,2})\s*(?:节)?$/);
      if (!match) continue;
      let container = element.parentElement;
      for (let depth=0; container && depth<4; depth++,container=container.parentElement) {
        const pair = timesIn(container.innerText || container.textContent);
        if (pair.length >= 2) { addCandidate(results,match[1],pair,15-depth); break; }
      }
    }
    const pageText = String((root.body && root.body.innerText) || root.innerText || "");
    const linePattern = /(?:^|\n)\s*(?:第\s*)?(\d{1,2})\s*(?:节)?\s*(?:\n|\s)+([01]?\d|2[0-3])\s*[:：]\s*([0-5]\d)\s*(?:\n|\s)+([01]?\d|2[0-3])\s*[:：]\s*([0-5]\d)/g;
    for (const match of pageText.matchAll(linePattern)) addCandidate(results,match[1],[String(match[2]).padStart(2,"0")+":"+match[3],String(match[4]).padStart(2,"0")+":"+match[5]],30);
    return results;
  }
  function dedupe(items) {
    const map = new Map();
    for (const item of items) {
      if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(item.start||"") || !/^([01]\d|2[0-3]):[0-5]\d$/.test(item.end||"")) continue;
      const old = map.get(item.section); if (!old || (item.score||0) > (old.score||0)) map.set(item.section,item);
    }
    return Array.from(map.values()).sort((a,b)=>a.section-b.section).map(({section,start,end})=>({section,start,end}));
  }
  async function requestJson(url, body) {
    const response = await fetch(url,{method:body?"POST":"GET",credentials:"include",redirect:"follow",headers:{"X-Requested-With":"XMLHttpRequest",...(body?{"Content-Type":"application/x-www-form-urlencoded; charset=UTF-8"}:{})},body:body?new URLSearchParams(body).toString():undefined});
    const responseText = await response.text();
    if (!response.ok) throw new Error(response.status+" "+response.statusText+": "+responseText.slice(0,180));
    if (/login_slogin|用户登录|用户名/.test(responseText) && responseText.trim().startsWith("<")) throw new Error("登录会话已失效，请重新登录");
    try { return {json:JSON.parse(responseText),text:responseText}; } catch (_) { throw new Error("接口未返回 JSON："+responseText.slice(0,180)); }
  }
  async function firstSuccessful(label, candidates) {
    for (const candidate of candidates) { try { const value=await candidate.run(); diagnostics.push(label+"：成功 "+candidate.name); return {value,name:candidate.name}; } catch(error) { diagnostics.push(label+"："+candidate.name+" 失败："+String(error&&error.message||error)); } }
    return null;
  }
  try {
    if (location.origin !== allowedOrigin || !location.pathname.startsWith(pathPrefix)) throw new Error("当前页面不在所选学校允许的教务范围内");
    const detected=detectTerm(document);
    const xnm=String(detected.xnm||options.xnm||"").trim(), xqm=String(detected.xqm||options.xqm||"").trim();
    if (detected.xnm && String(options.xnm||"").trim() && detected.xnm !== String(options.xnm).trim()) diagnostics.push("教务页面学年与顶部参数不一致，已以页面当前选择为准："+detected.xnm);
    if (detected.xqm && String(options.xqm||"").trim() && detected.xqm !== String(options.xqm).trim()) diagnostics.push("教务页面学期与顶部参数不一致，已以页面当前选择为准："+detected.xqm);
    if (!/^\d{4}$/.test(xnm)) throw new Error("学年 xnm 应为四位年份，例如 2025");
    if (!xqm) throw new Error("请填写学期代码 xqm（第一学期通常为 3，第二学期通常为 12）");
    const base=pathPrefix.endsWith("/")?pathPrefix.slice(0,-1):pathPrefix; const scheduleBody={xnm,xqm,kzlx:"ck"};
    const schedule=await firstSuccessful("课程表",[
      {name:"N2151 query",run:()=>requestJson(base+"/kbcx/xskbcx_cxXsKb.html?doType=query&gnmkdm=N2151",scheduleBody)},
      {name:"N2151",run:()=>requestJson(base+"/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151",scheduleBody)},
      {name:"N253508",run:()=>requestJson(base+"/kbcx/xskbcx_cxXsKb.html?doType=query&gnmkdm=N253508",scheduleBody)}
    ]);
    if (!schedule) throw new Error("无法读取课程表；请确认已经登录并可在教务中打开课表");
    const examBody={xnm,xqm,"_search":"false",nd:String(Date.now()),"queryModel.showCount":"1000","queryModel.currentPage":"1","queryModel.sortName":"","queryModel.sortOrder":"asc",time:"0"};
    const exams=await firstSuccessful("考试安排",[
      {name:"N358105",run:()=>requestJson(base+"/kwgl/kscx_cxXsksxxIndex.html?doType=query&gnmkdm=N358105",examBody)},
      {name:"N253508",run:()=>requestJson(base+"/kwgl/kscx_cxXsksxxIndex.html?doType=query&gnmkdm=N253508",examBody)}
    ]);
    let sectionCandidates=extractSectionTimes(document);
    for (const frame of document.querySelectorAll("iframe")) { try { sectionCandidates=sectionCandidates.concat(extractSectionTimes(frame.contentDocument)); } catch (_) {} }
    for (const pageUrl of [base+"/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default",base+"/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151"]) {
      try { const response=await fetch(pageUrl,{credentials:"include"}); const html=await response.text(); sectionCandidates=sectionCandidates.concat(extractSectionTimes(new DOMParser().parseFromString(html,"text/html"))); } catch(error) { diagnostics.push("作息页面读取失败："+String(error&&error.message||error)); }
    }
    const sectionTimes=dedupe(sectionCandidates);
    if (!sectionTimes.length) diagnostics.push("未从页面提取到节次真实时间。请切换教务课表的“表格”视图再读取，或导入后在设置中填写。课程不会套用错误的默认作息。");
    finish({ok:true,capturedAt,page:{url:location.href,title:document.title},term:{xnm,xqm,detectedFromPage:Boolean(detected.xnm&&detected.xqm)},sources:{courses:{ok:true,endpoint:schedule.name},exams:{ok:Boolean(exams),endpoint:exams?exams.name:"",count:exams?1:0},sectionTimes:{ok:sectionTimes.length>0,count:sectionTimes.length}},schedule:schedule.value.json,exams:exams?exams.value.json:{items:[]},sectionTimes,diagnostics});
  } catch(error) {
    diagnostics.push(String(error&&error.stack||error));
    finish({ok:false,capturedAt,page:{url:location.href,title:document.title},diagnostics});
  }
})();
