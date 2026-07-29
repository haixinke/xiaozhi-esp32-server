package xiaozhi.modules.pdc.nfc.service;

import xiaozhi.modules.pdc.nfc.vo.PdcNfcProductTypeVO;

import java.util.List;

/**
 * 商品类型服务：只读，提供列表查询和视图转换。
 * 商品类型的业务字段（typeCode/typeName 等）通过数据库初始化，不提供 CRUD 入口。
 */
public interface PdcNfcProductTypeService {

    /**
     * 查询所有商品类型（含微信配置状态和最新发布证据）。
     */
    List<PdcNfcProductTypeVO> list();
}
