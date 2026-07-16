# 教务 Provider 与正方适配器导入协议

## 1. 总体流程

Android 与 macOS 从随安装包发布的 providers/catalog.json 加载学校 Provider，根据 provider.json 打开该学校的 homePath。会话失效时，学校页面会重定向到 loginPath。

Android 用户可以主动保存教务用户名与密码；凭据由 Android Keystore AES-256-GCM 密钥按 Provider 分区，只在对应 Provider 的登录页填写。macOS 不单独持久化教务用户名与密码。验证码、统一身份认证或其他交互步骤始终由用户在学校页面中完成，验证码不保存。

内嵌浏览器只允许当前 Provider 的精确 HTTPS allowedOrigin、默认 443 端口和 pathPrefix。其他 HTTP(S) 链接交给系统浏览器，危险 scheme 被阻止。

## 2. Provider 输入

Provider 定义：

- 学校稳定 ID 与显示名称；
- 精确 origin/path 网络合同；
- 本地 Adapter ID；
- 语义学期 first / second / third 到学校代码的映射；
- 用于 Android WebView Profile 与 macOS WKWebsiteDataStore 的唯一会话 UUID。

第一教学周周一、总周数、学期名称和校区由“设置”中当前 Provider 所属的活动学期提供，不由接口猜测。导入脚本会收到当前 Provider 的 allowedOrigin / pathPrefix 以及该学期的厂商参数。

## 3. 当前已实现 Provider

当前 Catalog 只包含 nfu-zhengfang，对应广州南方学院正方教务：

~~~~text
allowedOrigin = https://jwxt.nfu.edu.cn
pathPrefix   = /jwglxt/
adapterId    = zhengfang-v1
第一学期     = 3
第二学期     = 12
第三学期     = 16
~~~~

以下接口、字段和解析规则属于 zhengfang-v1，不是所有学校的通用合同。

## 4. zhengfang-v1 课程接口

脚本按顺序尝试：

~~~~text
POST /jwglxt/kbcx/xskbcx_cxXsKb.html?doType=query&gnmkdm=N2151
POST /jwglxt/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151
POST /jwglxt/kbcx/xskbcx_cxXsKb.html?doType=query&gnmkdm=N253508
POST /jwglxt/kbcx/xskbcx_cxXsKb.html?doType=query
~~~~

请求携带当前 Provider 会话 Cookie，正文含 xnm、xqm、kzlx=ck。常见响应形状：

~~~~json
{
  "kbList": [
    {
      "kcmc": "合成课程甲",
      "xm": "虚构教师",
      "xqj": "2",
      "jc": "1-2节",
      "zcd": "1-18周",
      "cdmc": "测试楼 A-101"
    }
  ],
  "sjkList": []
}
~~~~

Adapter 兼容已测字段别名，并对 sjkList 中具备星期和节次的实践课进行结构化导入。

## 5. zhengfang-v1 考试接口

~~~~text
POST /jwglxt/kwgl/kscx_cxXsksxxIndex.html?doType=query&gnmkdm=N358105
POST /jwglxt/kwgl/kscx_cxXsksxxIndex.html?doType=query&gnmkdm=N253508
POST /jwglxt/kwgl/kscx_cxXsksxxIndex.html?doType=query
~~~~

正文含分页参数，并将 queryModel.showCount 设为 1000。常见响应形状：

~~~~json
{
  "items": [
    {
      "kcmc": "合成课程甲",
      "kssj": "2027-01-08 09:00-11:00",
      "cdmc": "测试楼 B-202",
      "cdxqmc": "测试校区",
      "zwh": "A1",
      "ksmc": "期末考试"
    }
  ]
}
~~~~

考试被映射为 EXAM + SINGLE，并使用教务明确提供的 date/startTime/endTime；不将考试伪装成周期课程或猜测节次。

## 6. 真实节次时间提取

脚本从当前页面表格、同源 iframe、带节次属性的节点和学生课表页面服务端 HTML 中收集候选，输出：

~~~~json
{"section": 1, "start": "08:00", "end": "08:40"}
~~~~

若无法提取，不会构造虚假作息。导入会显示诊断，用户也可以在设置中用板块化作息生成器维护真实时间。

## 7. 脚本输出与消息边界

脚本输出包含 ok、页面信息、检测到的学期、课程源、考试源、节次时间和诊断。不得包含密码、验证码、Cookie 或 Authorization。

Android 在主动读取期间注册限定当前 Provider 精确 origin 与主框架的一次性 CourseAxisBridge.postMessage 通道，收到结果后立即移除。macOS 通过 window.webkit.messageHandlers.courseAxis.postMessage 接收，同时验证主页面与 Provider 边界，并且只在主动采集期间处理。两端均不使用控制台输出作为结果回退。

## 8. 原生标准化与写入

AcademicImportAdapterRegistry 只返回显式注册的 Adapter，未知 ID 失败关闭。当前 ZhengfangImportAdapter 在两端执行：

- 周次：区间、离散周、单双周。
- 节次：连续区间、中文连接符和离散段。
- 星期：数字、中英文星期文本。
- 时间：中英文冒号和常见范围连接符。
- 稳定 ID：使用包含 Provider 的学期 ID 分区，Android 与 macOS 的 FNV-1a 64 位测试向量一致。
- 明确时间优先；否则按当学期 TimeRule 解析；仍失败则为 UNRESOLVED。

写入前必须同时满足课程源成功、至少一门课程且课程无未解析真实时间。考试源失败不会删除该 Provider/学期已有考试。

## 9. 扩展新学校

修改现有 Adapter 或新增 Adapter 时，只能编辑 providers/ 下的唯一共享资源树，禁止复制平台私有脚本。必须同时添加合成 fixture、两端 registry 和解析、会话隔离、资源打包测试。完整要求见 [Provider 开发与学校贡献](PROVIDER_DEVELOPMENT.md)。
