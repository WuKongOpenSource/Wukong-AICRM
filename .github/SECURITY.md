# Security Policy / 安全策略

## Supported Versions / 支持版本

Security fixes are generally provided for the latest release and the current
`main` branch. Please confirm that an issue still exists in the latest version
before reporting it.

Wukong AICRM 原则上仅为最新正式版本及当前 `main` 分支提供安全修复。报告前，
请先确认问题在最新版本中仍然存在。

| Version / 版本 | Supported / 是否支持 |
| --- | --- |
| Latest release and current `main` / 最新正式版本及当前 `main` 分支 | Yes / 是 |
| Older releases / 历史版本 | No / 否 |

## Reporting a Vulnerability / 报告安全漏洞

Thank you for helping keep Wukong AICRM and its users secure.

Please do not disclose suspected vulnerabilities through a public GitHub Issue,
Discussion, Pull Request, forum, social media post, or other public channel.
Submit reports through
[GitHub Private Vulnerability Reporting](https://github.com/WuKongOpenSource/Wukong-AICRM/security/advisories/new).

感谢您帮助提升 Wukong AICRM 及其用户的安全性。

如发现疑似安全漏洞，请勿通过公开的 GitHub Issue、Discussion、Pull Request、
论坛、社交媒体或其他公开渠道披露漏洞细节。请通过
[GitHub 私密漏洞报告](https://github.com/WuKongOpenSource/Wukong-AICRM/security/advisories/new)
提交。

Please include as much of the following information as possible:

- Affected version, branch, or commit
- Affected component, endpoint, and vulnerability type
- Prerequisites and permissions required for exploitation
- Complete reproduction steps and a minimal proof of concept
- Security impact and realistic attack scenarios
- Suggested remediation or mitigation, if available
- Your GitHub username and preferred credit

请尽可能提供以下信息：

- 受影响的版本、分支或提交
- 受影响的组件、接口及漏洞类型
- 利用所需的权限和前置条件
- 完整复现步骤及最小化 PoC
- 安全影响及现实攻击场景
- 可能的修复或缓解建议
- 报告者的 GitHub 用户名及署名要求

Redact credentials, personal data, and other sensitive information from reports
unless the minimum necessary detail is required to understand the vulnerability.

除理解漏洞所必需的最少信息外，请对访问凭据、个人数据及其他敏感信息进行脱敏。

## Disclosure Process / 处理与披露流程

Maintainers will review the report privately and may request additional
information. If the vulnerability is confirmed, we will coordinate impact
assessment, remediation, validation, release, and public disclosure with the
reporter.

Where appropriate, maintainers may publish a GitHub Security Advisory and
request a CVE identifier through GitHub. CVE eligibility and the number of CVE
identifiers are determined by the applicable GitHub and CVE rules and are not
guaranteed in advance.

Please allow a reasonable amount of time for investigation and remediation, and
do not publicly disclose vulnerability details before a fix is released and the
coordinated disclosure date is reached.

维护者将对报告进行私密审查，并可能要求报告者补充信息。漏洞确认后，我们将与
报告者协调完成影响评估、修复、验证、版本发布及公开披露。

符合条件时，维护者可能发布 GitHub Security Advisory，并通过 GitHub 申请 CVE
编号。是否符合 CVE 条件及可分配的编号数量取决于 GitHub 和 CVE 的适用规则，
不预先作出保证。

请为调查和修复预留合理时间。在修复版本发布及双方协调确定的公开日期之前，
请勿公开漏洞细节。

Unless explicitly announced otherwise, this reporting process is not a bug
bounty program and does not create a promise of payment or other compensation.

除非项目另行明确公告，本报告流程不构成漏洞奖励计划，也不承诺支付报酬或提供
其他补偿。

## Testing Boundaries / 安全测试边界

Security testing must be limited to systems and data owned by the researcher or
covered by explicit authorization. Do not:

- Test or disrupt an unauthorized production or online demonstration environment
- Access, download, modify, or disclose real user data
- Perform denial-of-service, social engineering, phishing, or supply-chain attacks
- Cause data loss, service interruption, or impact to other users
- Use access obtained during testing to probe unrelated systems

安全测试仅限于测试者拥有或已取得明确授权的系统和数据。请勿：

- 测试或破坏未经授权的生产环境及在线演示环境
- 访问、下载、修改或传播真实用户数据
- 进行拒绝服务、社会工程、钓鱼或供应链攻击
- 执行可能造成数据丢失、服务中断或影响其他用户的操作
- 使用测试中获得的访问权限进一步探测无关系统

If sensitive data is encountered unintentionally, stop testing immediately and
describe the situation in the private report.

如意外接触敏感数据，请立即停止测试，并在私密漏洞报告中说明情况。
