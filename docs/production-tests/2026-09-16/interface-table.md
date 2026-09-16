| 角色 / 接口 | HTTP / 业务码 | 端到端秒（按测量顺序） |
|---|---|---|
| admin `/auth/userInfo` | 200/200 | 1.102424, 0.728205 |
| admin `/data/dashboard/statistics` | 200/200 | 1.377373, 0.877544 |
| admin `/data/dashboard/trend?period=week` | 200/200 | 1.226015, 0.746655 |
| admin `/data/dashboard/distribution` | 200/200 | 0.878044, 0.801905 |
| admin `/data/dashboard/recentLogins` | 200/200 | 1.855796, 0.874266 |
| admin `/teacher/list?pageNum=1&pageSize=10` | 200/200 | 1.004883, 0.826421 |
| admin `/teacher/1` | 200/200 | 0.815097, 0.822443 |
| admin `/student/list?pageNum=1&pageSize=10` | 200/200 | 1.158502, 0.901346 |
| admin `/student/1` | 200/200 | 0.749914, 0.879751 |
| admin `/mental/overview` | 200/200 | 0.896406, 1.005603 |
| admin `/mental/analysis` | 200/200 | 0.990550, 1.141729 |
| admin `/mental/questionnaires?pageNum=1&pageSize=10` | 200/200 | 0.909543, 1.307091 |
| admin `/mental/questionnaires/1/full` | 200/200 | 1.467178, 0.770500 |
| admin `/mental/questionnaires/1/completion` | 200/200 | 0.897655, 1.004988 |
| admin `/agent/api/v1/task/list?pageNum=1&pageSize=10` | 429/— | 0.770675, 0.698432 |
| admin `/user/list?page=1&size=10` | 429/—, 200/200 | 0.956576, 0.690293, 6.646943, 0.763697 |
| admin `/student/1/academic` | 200/200 | 1.421119, 0.989557 |
| admin `/student/1/attendance/summary` | 200/200 | 1.432553, 0.985658 |
| admin `/agent/api/v1/warning/stream` | 000/— | 60.006053, 60.005751 |
| admin `/teacher/list?page=1&size=10` | 200/200 | 0.806944, 0.842607 |
| admin `/student/list?page=1&size=10` | 200/200 | 0.806956, 0.839756 |
| admin `/agent/api/v1/task/list?page=1&size=20` | 000/— | 60.005436, 60.004488 |
| teacher `/auth/login` | 200/200 | 1.913198 |
| teacher `/auth/userInfo` | 200/200 | 0.757001 |
| teacher `/mental/overview` | 200/200 | 0.965630 |
| student `/auth/login` | 200/200 | 1.236330 |
| student `/auth/userInfo` | 200/200 | 0.737010 |
| student `/mental/student/questionnaires?userId=3` | 200/200 | 0.909368 |
| student `/mental/student/assessments?userId=3` | 200/200 | 0.894724 |
