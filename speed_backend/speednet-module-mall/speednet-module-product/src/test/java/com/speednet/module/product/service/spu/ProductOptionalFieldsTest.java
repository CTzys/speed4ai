package com.speednet.module.product.service.spu;
import com.speednet.module.product.controller.admin.spu.vo.*;
import com.speednet.framework.common.util.json.JsonUtils;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ProductOptionalFieldsTest {
 @Test void nameAndSkuPriceStockAreEnoughWithoutMerchandisingOrLogistics(){var r=JsonUtils.parseObject("{\"name\":\"商品\",\"categoryId\":null,\"brandId\":null,\"description\":null,\"giveIntegral\":null,\"skus\":[{\"price\":0,\"stock\":0,\"picUrl\":null,\"costPrice\":null}]}",ProductSpuSaveReqVO.class);try(var f=Validation.buildDefaultValidatorFactory()){assertTrue(f.getValidator().validate(r).isEmpty());}assertEquals(0,r.getGiveIntegral());assertFalse(r.getSpecType());assertTrue(r.getDeliveryTypes().isEmpty());assertEquals(0,r.getSkus().getFirst().getCostPrice());}
 @Test void realSaleInputsRemainRequiredAndNonnegative(){var r=JsonUtils.parseObject("{\"name\":\" \" ,\"skus\":[{\"price\":-1,\"stock\":-1}]}",ProductSpuSaveReqVO.class);try(var f=Validation.buildDefaultValidatorFactory()){assertEquals(3,f.getValidator().validate(r).size());}r.setName("商品");r.getSkus().getFirst().setPrice(null);r.getSkus().getFirst().setStock(null);try(var f=Validation.buildDefaultValidatorFactory()){assertEquals(2,f.getValidator().validate(r).size());}}
}
