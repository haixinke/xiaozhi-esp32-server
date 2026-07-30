export const buildReleaseEvidencePayload = ({ releaseVersion, publishedAt, smokeEvidence }) => ({
  releaseVersion,
  publishedAt,
  smokeEvidence
})

export const buildReleaseEvidenceViewModel = (evidence = {}) => ({
  releaseVersion: evidence.releaseVersion || '',
  publishedAt: evidence.publishedAt || '',
  smokeEvidence: evidence.smokeEvidence || ''
})
