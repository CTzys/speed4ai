package com.speednet.module.product.convert.spu;

import com.speednet.framework.common.util.collection.CollectionUtils;
import com.speednet.framework.common.util.object.BeanUtils;
import com.speednet.module.product.controller.admin.spu.vo.ProductSkuRespVO;
import com.speednet.module.product.controller.admin.spu.vo.ProductSpuPageReqVO;
import com.speednet.module.product.controller.admin.spu.vo.ProductSpuRespVO;
import com.speednet.module.product.controller.app.spu.vo.AppProductSpuPageReqVO;
import com.speednet.module.product.dal.dataobject.sku.ProductSkuDO;
import com.speednet.module.product.dal.dataobject.spu.ProductSpuDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Map;

import static com.speednet.framework.common.util.collection.CollectionUtils.convertMultiMap;

/**
 * 商品 SPU Convert
 *
 * @author SpeedNet
 */
@Mapper
public interface ProductSpuConvert {

    ProductSpuConvert INSTANCE = Mappers.getMapper(ProductSpuConvert.class);

    ProductSpuPageReqVO convert(AppProductSpuPageReqVO bean);

    default ProductSpuRespVO convert(ProductSpuDO spu, List<ProductSkuDO> skus) {
        ProductSpuRespVO spuVO = BeanUtils.toBean(spu, ProductSpuRespVO.class);
        spuVO.setSkus(BeanUtils.toBean(skus, ProductSkuRespVO.class));
        return spuVO;
    }

    default List<ProductSpuRespVO> convertForSpuDetailRespListVO(List<ProductSpuDO> spus, List<ProductSkuDO> skus) {
        Map<Long, List<ProductSkuDO>> skuMultiMap = convertMultiMap(skus, ProductSkuDO::getSpuId);
        return CollectionUtils.convertList(spus, spu -> convert(spu, skuMultiMap.get(spu.getId())));
    }

}
