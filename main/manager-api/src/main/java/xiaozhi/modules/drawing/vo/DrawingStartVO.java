package xiaozhi.modules.drawing.vo;

/** 成功开局只返回局次与参考图，不承诺完成时的结算金额。 */
public record DrawingStartVO(String sessionId, String referenceId) { }
