# Debug Session: structure-empty-annotations
- **Status**: [CLOSED]
- **Issue**: 酶库中心右侧 3D 结构区域空白；自动初始注释未能从 UniProt / PDB 成功导入。
- **Debug Server**: http://127.0.0.1:7777/event
- **Log File**: .dbg/trae-debug-log-structure-empty-annotations.ndjson

## Reproduction Steps
1. 打开 `http://localhost:5173/library/imported`
2. 选中一个已导入酶条目
3. 观察右侧 3D 结构区域是否空白
4. 观察“自动导入（UniProt / PDB）”统计是否仍为 0

## Hypotheses & Verification
| ID | Hypothesis | Likelihood | Effort | Evidence |
|----|------------|------------|--------|----------|
| A | 传给 StructureViewer 的结构参数错误，导致请求了错误结构源 | High | Low | Pending |
| B | Molstar 已初始化但容器尺寸或重绘链路异常，导致白屏 | Medium | Low | Pending |
| C | 结构文件实际加载失败，但前端状态没有稳定落到错误态 | High | Low | Pending |
| D | 自动补注释在前端就被门槛条件拦住，没有真正发起 | High | Low | Pending |
| E | 自动补注释发起了，但接口返回空或报错 | Medium | Medium | Pending |

## Log Evidence
- `StructureViewer.vue:initViewer`: `width=390/531`, `height=0`
- `StructureViewer.vue:loadStructure`: `3BYH` PDB load success
- `EnzymesPage.vue:autoImportWatch`: `enzymeId=14`, `uniprotAccession=P60709`, `pdbId=3BYH`, auto import started and finished with `importedCount=0`
- Runtime network: `GET https://files.rcsb.org/download/3BYH.cif` succeeded
- Runtime network: `POST /api/enzymes/14/annotations/import-uniprot` returned successfully but no imported annotations appeared
- External evidence:
  - UniProt `P60709` feature types include `Natural variant` and `Mutagenesis`
  - RCSB `3BYH.A` feature types include `ECOD`
- Backend log (post-rebuild): import failed on one UniProt feature with `Data too long for column 'title' at row 1`
- API verification (post-fix): `POST /api/enzymes/14/annotations/import-uniprot` imported `22` records successfully
- API verification after restart: repeated import returned `0`, while `GET /api/enzymes/14/annotations` returned `22` records, confirming idempotent behavior

## Verification Conclusion
| ID | Hypothesis | Status | Evidence Summary |
|----|------------|--------|------------------|
| A | 传给 StructureViewer 的结构参数错误，导致请求了错误结构源 | Rejected | 已记录 `structureId=3BYH`, `structureSourceDb=PDB`, `selectedViewerStructureId=3BYH` |
| B | Molstar 已初始化但容器尺寸或重绘链路异常，导致白屏 | Confirmed | `init viewer requested/plugin created` 时 `height=0` |
| C | 结构文件实际加载失败，但前端状态没有稳定落到错误态 | Rejected | `GET 3BYH.cif` 成功，埋点记录 `load PDB success` |
| D | 自动补注释在前端就被门槛条件拦住，没有真正发起 | Rejected | 埋点记录 `auto import started` |
| E | 自动补注释发起了，但接口返回空或报错 | Confirmed | 先定位到 UniProt 特征类型映射不兼容，修复后又暴露出数据库 `title` 长度不足，最终通过映射修正 + 文本长度保护 + 表结构扩容解决 |

## Final Resolution
- 3D 白屏根因是 `StructureViewer` 挂载容器高度为 `0`，修复容器高度后，Molstar 可以正常渲染结构。
- 自动初始注释失败经历了两层根因：
  1. UniProt 返回的特征类型使用 `Natural variant` / `Mutagenesis` 等人类可读值，原有映射无法识别。
  2. 识别后写库时，部分注释标题过长，触发 MySQL `Data too long for column 'title'`。
- 最终修复包括：
  - 扩展 `UniProtClient` 的特征类型映射；
  - 在 `NcbiImportService` 中为 `title / mutationLabel / sourceRef` 增加长度保护；
  - 新增 Flyway 迁移 `V10__expand_enzyme_annotation_text_fields.sql`，将 `enzyme_annotation.title` 扩容到 `VARCHAR(512)`，并将 `mutation_label` 扩容到 `VARCHAR(255)`。

## Result
- 后端重新打包并启动后，Flyway 已成功迁移到 `v10`。
- 对 `enzymeId=14` 的真实接口验证结果：
  - 首次导入成功写入 `22` 条 `UNIPROT` 注释；
  - 再次导入返回 `0` 条新增，现有注释总数保持 `22`，说明导入已打通且不会重复写入。
