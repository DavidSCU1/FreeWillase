export const mockLiteratureCandidates = [
  {
    id: 1,
    title: 'Crystal structure and catalytic mechanism of a cellulase esterase complex',
    authors: 'Li H., Wang J., et al.',
    journal: 'Journal of Enzyme Engineering',
    year: 2024,
    confidence: 'STRONG',
    basis: 'Accession Direct Link + Species Consistency (TaxID: 9606)',
    summary: '聚焦纤维素酶/酯酶复合体的结构域组织和催化残基，为后续位点映射提供强证据。',
  },
  {
    id: 2,
    title: 'Functional annotation of actin-associated ATPase activities in human cytoplasmic proteins',
    authors: 'Kim S., Zhou Y.',
    journal: 'Proteome Informatics',
    year: 2023,
    confidence: 'WEAK',
    basis: 'Protein Name Match + Species Consistency',
    summary: '与条目存在功能相关性，基于语义分析发现其在胞质蛋白中的保守性。',
  },
  {
    id: 3,
    title: 'Domain-aware retrieval for enzyme sequence literature linking',
    authors: 'Garcia P., Muller T.',
    journal: 'Computational Biology Reports',
    year: 2025,
    confidence: 'CANDIDATE',
    basis: 'Semantic Similarity in Abstract',
    summary: '通过对摘要的语义检索发现其在酶序列文献链接中的潜在关联，待进一步规则验证。',
  },
]

export const predictionModules = [
  {
    name: 'ss_generator.py',
    purpose: '二级结构候选生成',
    status: '集成状态: 已就绪 (API Mode)',
  },
  {
    name: 'backbone_predictor.py',
    purpose: '骨架预测',
    status: '集成状态: 等待计算资源分配',
  },
  {
    name: 'assembler.py',
    purpose: '结构拼装',
    status: '集成状态: 已就绪 (Distributed Mode)',
  },
  {
    name: 'quality.py',
    purpose: '质量评估',
    status: '集成状态: 结果回调监听中',
  },
]
