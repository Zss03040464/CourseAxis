import { useEffect, useRef, useState } from "react";
import {
  ArrowLeft,
  ArrowRight,
  CalendarBlank,
  Code,
  GithubLogo,
  Heart,
  LockKey,
  Plus,
  ShieldCheck,
  X,
} from "@phosphor-icons/react";

import { buildSiteConfig } from "./siteConfig.js";
import { preferredScrollBehavior } from "./motion.js";


const siteConfig = buildSiteConfig(import.meta.env);

const DAYS = [
  ["一", "9/7"],
  ["二", "9/8"],
  ["三", "9/9"],
  ["四", "9/10"],
  ["五", "9/11"],
  ["六", "9/12"],
  ["日", "9/13"],
];

const TIMES = ["08:00", "08:50", "09:45", "10:35", "11:20", "14:30", "16:10", "20:15"];

const SYNTHETIC_EVENTS = [
  {
    title: "合成课程甲",
    meta: "10:35 · 示例校区",
    tone: "violet",
    column: 0,
    top: 24,
    height: 23,
  },
  {
    title: "合成课程乙",
    meta: "10:35 · 虚构教师乙",
    tone: "coral",
    column: 2,
    top: 24,
    height: 23,
  },
  {
    title: "合成考试甲",
    meta: "14:30 · 示例楼 407",
    tone: "green",
    column: 4,
    top: 52,
    height: 26,
  },
  {
    title: "合成课程丙",
    meta: "20:15 · 第 1–13 周",
    tone: "indigo",
    column: 3,
    top: 78,
    height: 20,
  },
];

const DIALOG_CONTENT = {
  github: {
    title: "公开仓库尚未发布",
    body: "GitHub 地址会在安全开源流程完成并获得最终发布授权后启用。当前页面不会跳转到占位地址。",
  },
  sponsor: {
    title: "赞助通道尚未开放",
    body: "维护者尚未配置外部 HTTPS 赞助页。CourseAxis 的全部功能仍然永久免费，稍后开放赞助也不会附带任何权益。",
  },
};


function SchedulePreview() {
  return (
    <section className="schedule-preview" aria-label="完全合成的 CourseAxis 周课表示例">
      <div className="schedule-toolbar">
        <div>
          <strong>2026/9/7</strong>
          <span>第 1 周 · 4 项</span>
        </div>
        <div className="schedule-toolbar-icons" aria-hidden="true">
          <span><ArrowLeft weight="bold" /></span>
          <span><ArrowRight weight="bold" /></span>
          <span><Plus weight="bold" /></span>
        </div>
      </div>

      <div className="term-control">
        <CalendarBlank weight="duotone" />
        <span><small>当前学期</small>2026–2027 第一学期</span>
        <ArrowRight className="term-chevron" weight="bold" />
      </div>

      <div className="filter-strip" aria-hidden="true">
        <span className="active">全部</span>
        <span>课程</span>
        <span>考试</span>
      </div>

      <div className="calendar-days" aria-hidden="true">
        <span />
        {DAYS.map(([weekday, date]) => (
          <span key={date}><b>{weekday}</b><small>{date}</small></span>
        ))}
      </div>

      <div className="calendar-body">
        <div className="calendar-times" aria-hidden="true">
          {TIMES.map((time, index) => (
            <span key={time}><b>{index + 1}</b><small>{time}</small></span>
          ))}
        </div>
        <div className="calendar-grid" aria-hidden="true">
          {Array.from({ length: 56 }, (_, index) => <span key={index} />)}
        </div>
        {SYNTHETIC_EVENTS.map((event) => (
          <article
            className={`schedule-event ${event.tone}`}
            key={event.title}
            style={{
              "--event-column": event.column,
              "--event-top": `${event.top}%`,
              "--event-height": `${event.height}%`,
            }}
          >
            <strong>{event.title}</strong>
            <span>{event.meta}</span>
          </article>
        ))}
      </div>
    </section>
  );
}


function StatusDialog({ kind, onClose }) {
  const closeRef = useRef(null);
  const dialogRef = useRef(null);
  const content = DIALOG_CONTENT[kind];

  useEffect(() => {
    closeRef.current?.focus();
    const handleKeyDown = (event) => {
      if (event.key === "Escape") {
        onClose();
        return;
      }
      if (event.key !== "Tab") return;

      const focusable = [...(dialogRef.current?.querySelectorAll("button") ?? [])];
      const first = focusable[0];
      const last = focusable.at(-1);
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last?.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first?.focus();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  return (
    <div className="dialog-backdrop" onMouseDown={onClose}>
      <section
        aria-describedby="status-dialog-body"
        aria-labelledby="status-dialog-title"
        aria-modal="true"
        className="status-dialog"
        onMouseDown={(event) => event.stopPropagation()}
        ref={dialogRef}
        role="dialog"
      >
        <button aria-label="关闭" className="dialog-close" onClick={onClose} ref={closeRef}>
          <X weight="bold" />
        </button>
        <span className="dialog-icon" aria-hidden="true">
          {kind === "sponsor" ? <Heart weight="duotone" /> : <GithubLogo weight="duotone" />}
        </span>
        <h2 id="status-dialog-title">{content.title}</h2>
        <p id="status-dialog-body">{content.body}</p>
        <button className="dialog-confirm" onClick={onClose}>我知道了</button>
      </section>
    </div>
  );
}


export function App() {
  const [dialogKind, setDialogKind] = useState(null);
  const dialogTriggerRef = useRef(null);

  const closeDialog = () => {
    const trigger = dialogTriggerRef.current;
    setDialogKind(null);
    window.requestAnimationFrame(() => trigger?.focus());
  };

  const openDestination = (kind, destination) => {
    if (destination) {
      window.open(destination, "_blank", "noopener,noreferrer");
      return;
    }
    dialogTriggerRef.current = document.activeElement;
    setDialogKind(kind);
  };

  const scrollToSponsor = () => {
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    document.querySelector("#sponsor")?.scrollIntoView({
      behavior: preferredScrollBehavior(reducedMotion),
    });
  };

  return (
    <main className="site-shell">
      <a className="skip-link" href="#main-content">跳到主要内容</a>

      <section className="hero" id="main-content">
        <div className="hero-copy">
          <div className="brand-lockup">
            <img alt="CourseAxis 应用图标" src="./courseaxis-app-icon.png" />
            <div><strong>CourseAxis</strong><span>星轨时间轴</span></div>
          </div>

          <p className="eyebrow">COURSEAXIS · 免费开源课表</p>
          <h1>让课程回到<br /><span>真实时间里</span></h1>
          <p className="hero-description">免费、开源、跨 Android 与 macOS 的真实时间课表。</p>

          <ul className="trust-list" aria-label="项目承诺">
            <li><ShieldCheck weight="duotone" />永久免费</li>
            <li><Code weight="duotone" />MIT 开源</li>
            <li><LockKey weight="duotone" />本地优先</li>
          </ul>

          <div className="hero-actions">
            <button className="primary-action" onClick={() => openDestination("github", siteConfig.githubUrl)}>
              <GithubLogo weight="fill" />查看 GitHub 源码
            </button>
            <button className="text-action" onClick={scrollToSponsor}>
              了解自愿赞助<ArrowRight weight="bold" />
            </button>
          </div>
        </div>

        <SchedulePreview />
      </section>

      <section className="sponsor-section" id="sponsor">
        <span className="sponsor-icon" aria-hidden="true"><Heart weight="duotone" /></span>
        <div className="sponsor-copy">
          <p className="eyebrow">完全自愿 · 不影响使用</p>
          <h2>如果 CourseAxis 对你有帮助</h2>
          <p>你的赞助将用于支持应用的开发、测试与发布，帮助 CourseAxis 持续改进、稳定运行，并适配更多设备与学校系统。</p>
          <div className="sponsor-actions">
            <button className="sponsor-action" onClick={() => openDestination("sponsor", siteConfig.sponsorUrl)}>
              <Heart weight="fill" />自愿赞助
            </button>
            <strong>赞助不解锁功能、服务或优先级</strong>
          </div>
        </div>
        <div className="affiliation-note">
          <ShieldCheck weight="duotone" />
          <p>本项目与学校及教务厂商无官方隶属关系</p>
        </div>
      </section>

      <footer>
        <span>CourseAxis · MIT License</span>
        <span>无分析、无广告、无支付 SDK</span>
        <nav aria-label="许可证">
          <a href="./LICENSE.txt">项目许可证</a>
          <a href="./THIRD_PARTY_NOTICES.txt">第三方许可</a>
        </nav>
      </footer>

      {dialogKind && <StatusDialog kind={dialogKind} onClose={closeDialog} />}
    </main>
  );
}
