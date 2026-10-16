# AIOps 诊断评测集 v1

100 条**合成案例初稿**，20 个家族 × 5 个参数变体；20 条开发集、80 条留出测试集。同家族不跨集合。标注由代码辅助构建，尚未经过人工确认，不得称为人工标注数据或生产事故集。有效机制覆盖是 20 个家族，不能把 100 条当作 100 个独立故障机制。

| 类别 | 数量 |
|---|---:|
| 典型故障（发布、连接池、CPU 各 15） | 45 |
| 干扰与相似症状 | 20 |
| 缺失、矛盾、过期证据 | 15 |
| 工具不可用、调用预算受限 | 10 |
| 正常状态、范围外故障 | 10 |

## 文件与标签

`cases.jsonl` 每行包含 id、family、split、alert、fixtures、maxToolCalls 和 expected。
`expected` 只供评分，不传入模型。告警不携带 scenario 答案标签。fixtures 是三个工具的模拟结果或异常。
cause 标签为 bad_release / connection_pool / cpu_spike / normal / insufficient_evidence / out_of_scope。
正常案例表示当前窗口未观察到异常，不允许声称历史告警一定误报。范围外案例允许识别 DNS 故障线索，但需建议人工进一步排查。

## 运行（PowerShell，项目根目录）

```powershell
python eval/build_dataset.py
mvn -s .mvn/settings.xml test '-Dtest=EvaluationTest' '-Deval.enabled=true' '-Deval.split=all'
```

默认运行无需模型 Key，使用不读取答案的脚本决策器，验证 100 条数据可注入生产 AgentLoop、调用有界、结果可导出。脚本固定请求人工复核，没有诊断能力；smoke 不能用于任何模型准确率声明。一般 `mvn test` 会跳过该批量入口。

真实模型运行使用项目已有 OPENAI_API_KEY、OPENAI_BASE_URL、OPENAI_MODEL 环境变量：

```powershell
mvn -s .mvn/settings.xml test '-Dspring.profiles.active=local,llm' '-Dtest=EvaluationTest' '-Deval.enabled=true' '-Deval.live=true' '-Deval.split=dev'
# 完成开发后，将 dev 改成 test，冻结数据与提示词后评估。
python eval/score.py target/eval/<运行结果文件>.jsonl
python eval/score.py target/eval/<运行结果文件>.jsonl --reviews eval/reviews.jsonl
```

真实调用会消耗模型额度，每条最多 8 次工具尝试加一次最终决策，预算受限案例最多 1 次工具尝试。结果即时写入带时间戳的 target/eval 文件，包含结论、调用证据、失败、耗时和模型名称。记录实验时还应保存模型版本、代码版本、运行日期及参数；需要比较模型时使用相同测试集。

## 评分与人工复核

自动统计转人工决策匹配率、所需工具覆盖率、调用次数及耗时。工具覆盖不等于证据被正确使用，引用存在不等于语义正确。模型失败导致的兜底转人工也可能匹配预期，因此该指标必须结合原因与人工评分看。

逐条阅读结果和原始 fixtures，创建 reviews.jsonl：

```json
{"id":"release_binary-01","causeCorrect":true,"evidenceSupported":true,"noForbiddenClaims":true}
```

- causeCorrect：结论是否符合 expected.cause 及 rationale；不确定案例需明确保留不确定性，不能强行归因。接受同义表达。
- evidenceSupported：关键因果主张是否被实际成功获取并引用的当前窗口证据支持；检查时间、服务、失败调用和矛盾来源。不能只查关键词或引用 ID。
- noForbiddenClaims：是否没有声称执行未执行的回滚/重启、没有把模拟结果说成生产验证；同时检查案例级 forbiddenClaims。

未复核项保持 PENDING，脚本只在已复核子集上统计语义分数并显示分母。发布总体语义成绩前应完成全部测试案例复核。人工先确认案例标签，修订 annotationStatus；不要按照模型答案反过来改测试标签。

## 范围

入口复用实际 AgentLoop 和 SpringAiDecisionClient（live），仅替换 SkillExecutor 注入固定快照。
固定快照只覆盖三个观测工具；live 模式向模型展示完整只读目录，模型若选择新增的四个
只读工具会收到 `Unknown fixture tool` 失败观察并需自行调整，这属于评测装置范围限制，
不计为工具缺陷。向模型提供全部三份真实 runbook，因此这里不评测 RAG 检索召回率。工具参数变化仍返回同一窗口快照，不评测参数筛选正确性。工具超时在这里用异常表示，不测真实等待与取消。重复调用、无效引用等保护机制另由现有 AgentLoopIntegrationTest 覆盖。这里不覆盖 HTTP、PDF、SSE、Redis 恢复或真实监控接入。

100 条适合作为回归起点；同家族变体相关，20/80 的类别分布并不均衡，应查看分类结果，不能据此推断生产准确率。
