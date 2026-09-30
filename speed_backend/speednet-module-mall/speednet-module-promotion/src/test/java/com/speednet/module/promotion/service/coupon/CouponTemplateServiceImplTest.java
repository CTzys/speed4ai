package com.speednet.module.promotion.service.coupon;

import com.speednet.framework.common.enums.CommonStatusEnum;
import com.speednet.framework.common.pojo.PageResult;
import com.speednet.framework.common.util.collection.ArrayUtils;
import com.speednet.framework.test.core.ut.BaseDbUnitTest;
import com.speednet.module.product.api.category.ProductCategoryApi;
import com.speednet.module.product.api.spu.ProductSpuApi;
import com.speednet.module.promotion.controller.admin.coupon.vo.template.CouponTemplateCreateReqVO;
import com.speednet.module.promotion.controller.admin.coupon.vo.template.CouponTemplatePageReqVO;
import com.speednet.module.promotion.controller.admin.coupon.vo.template.CouponTemplateUpdateReqVO;
import com.speednet.module.promotion.dal.dataobject.coupon.CouponTemplateDO;
import com.speednet.module.promotion.dal.mysql.coupon.CouponTemplateMapper;
import com.speednet.module.promotion.enums.common.PromotionDiscountTypeEnum;
import com.speednet.module.promotion.enums.common.PromotionProductScopeEnum;
import com.speednet.module.promotion.enums.coupon.CouponTakeTypeEnum;
import com.speednet.module.promotion.enums.coupon.CouponTemplateValidityTypeEnum;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.function.Consumer;

import static com.speednet.framework.common.util.date.LocalDateTimeUtils.buildTime;
import static com.speednet.framework.common.util.object.ObjectUtils.cloneIgnoreId;
import static com.speednet.framework.test.core.util.AssertUtils.assertPojoEquals;
import static com.speednet.framework.test.core.util.AssertUtils.assertServiceException;
import static com.speednet.framework.test.core.util.RandomUtils.randomLongId;
import static com.speednet.framework.test.core.util.RandomUtils.randomPojo;
import static com.speednet.module.promotion.enums.ErrorCodeConstants.COUPON_TEMPLATE_NOT_ENOUGH;
import static com.speednet.module.promotion.enums.ErrorCodeConstants.COUPON_TEMPLATE_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.*;

/**
* {@link CouponTemplateServiceImpl} 的单元测试类
*
* @author SpeedNet
*/
@Import(CouponTemplateServiceImpl.class)
public class CouponTemplateServiceImplTest extends BaseDbUnitTest {

    @Resource
    private CouponTemplateServiceImpl couponTemplateService;

    @Resource
    private CouponTemplateMapper couponTemplateMapper;

    @MockitoBean
    private ProductCategoryApi productCategoryApi;
    @MockitoBean
    private ProductSpuApi productSpuApi;

    @Test
    public void testCreateCouponTemplate_success() {
        // 准备参数
        CouponTemplateCreateReqVO reqVO = randomPojo(CouponTemplateCreateReqVO.class, o -> {
            o.setTotalCount(10);
            o.setTakeLimitCount(2);
            o.setTakeType(CouponTakeTypeEnum.USER.getType());
            o.setProductScope(PromotionProductScopeEnum.ALL.getScope());
            o.setProductScopeValues(null);
            o.setValidityType(CouponTemplateValidityTypeEnum.TERM.getType());
            o.setFixedStartTerm(0);
            o.setFixedEndTerm(7);
            o.setDiscountType(PromotionDiscountTypeEnum.PRICE.getType());
            o.setDiscountPrice(100);
        });

        // 调用
        Long couponTemplateId = couponTemplateService.createCouponTemplate(reqVO);
        // 断言
        assertNotNull(couponTemplateId);
        // 校验记录的属性是否正确
        CouponTemplateDO couponTemplate = couponTemplateMapper.selectById(couponTemplateId);
        assertPojoEquals(reqVO, couponTemplate);
    }

    @Test
    public void testUpdateCouponTemplate_success() {
        // mock 数据
        CouponTemplateDO dbCouponTemplate = randomCouponTemplateDO();
        couponTemplateMapper.insert(dbCouponTemplate);// @Sql: 先插入出一条存在的数据
        // 准备参数
        CouponTemplateUpdateReqVO reqVO = randomPojo(CouponTemplateUpdateReqVO.class, o -> {
            o.setId(dbCouponTemplate.getId()); // 设置更新的 ID
            o.setTotalCount(10);
            o.setTakeLimitCount(2);
            o.setTakeType(CouponTakeTypeEnum.USER.getType());
            o.setProductScope(PromotionProductScopeEnum.ALL.getScope());
            o.setProductScopeValues(null);
            o.setValidityType(CouponTemplateValidityTypeEnum.TERM.getType());
            o.setFixedStartTerm(0);
            o.setFixedEndTerm(7);
            o.setDiscountType(PromotionDiscountTypeEnum.PRICE.getType());
            o.setDiscountPrice(100);
        });

        // 调用
        couponTemplateService.updateCouponTemplate(reqVO);
        // 校验是否更新正确
        CouponTemplateDO couponTemplate = couponTemplateMapper.selectById(reqVO.getId()); // 获取最新的
        assertPojoEquals(reqVO, couponTemplate);
    }

    @Test
    public void testUpdateCouponTemplate_notExists() {
        // 准备参数
        CouponTemplateUpdateReqVO reqVO = randomPojo(CouponTemplateUpdateReqVO.class, o -> o.setId(randomLongId()));

        // 调用, 并断言异常
        assertServiceException(() -> couponTemplateService.updateCouponTemplate(reqVO), COUPON_TEMPLATE_NOT_EXISTS);
    }

    @Test
    public void testCreateCouponTemplate_takeLimitCountGreaterThanTotalCount() {
        // 准备参数
        CouponTemplateCreateReqVO reqVO = randomPojo(CouponTemplateCreateReqVO.class, o -> {
            o.setTotalCount(1);
            o.setTakeLimitCount(2);
            o.setTakeType(CouponTakeTypeEnum.USER.getType());
            o.setProductScope(PromotionProductScopeEnum.ALL.getScope());
            o.setProductScopeValues(null);
            o.setValidityType(CouponTemplateValidityTypeEnum.TERM.getType());
            o.setFixedStartTerm(0);
            o.setFixedEndTerm(7);
            o.setDiscountType(PromotionDiscountTypeEnum.PRICE.getType());
            o.setDiscountPrice(100);
        });

        // 调用，并断言
        assertServiceException(() -> couponTemplateService.createCouponTemplate(reqVO), COUPON_TEMPLATE_NOT_ENOUGH);
    }

    @Test
    public void testUpdateCouponTemplate_takeLimitCountGreaterThanTotalCount() {
        // mock 数据
        CouponTemplateDO couponTemplate = randomCouponTemplateDO();
        couponTemplateMapper.insert(couponTemplate);
        // 准备参数
        CouponTemplateUpdateReqVO reqVO = randomPojo(CouponTemplateUpdateReqVO.class, o -> {
            o.setId(couponTemplate.getId()); // 设置更新的 ID
            o.setTotalCount(1);
            o.setTakeLimitCount(2);
            o.setTakeType(CouponTakeTypeEnum.USER.getType());
            o.setProductScope(PromotionProductScopeEnum.ALL.getScope());
            o.setProductScopeValues(null);
            o.setValidityType(CouponTemplateValidityTypeEnum.TERM.getType());
            o.setFixedStartTerm(0);
            o.setFixedEndTerm(7);
            o.setDiscountType(PromotionDiscountTypeEnum.PRICE.getType());
            o.setDiscountPrice(100);
        });

        // 调用，并断言
        assertServiceException(() -> couponTemplateService.updateCouponTemplate(reqVO), COUPON_TEMPLATE_NOT_ENOUGH);
    }

    @Test
    public void testDeleteCouponTemplate_success() {
        // mock 数据
        CouponTemplateDO dbCouponTemplate = randomCouponTemplateDO();
        couponTemplateMapper.insert(dbCouponTemplate);// @Sql: 先插入出一条存在的数据
        // 准备参数
        Long id = dbCouponTemplate.getId();

        // 调用
        couponTemplateService.deleteCouponTemplate(id);
       // 校验数据不存在了
       assertNull(couponTemplateMapper.selectById(id));
    }

    @Test
    public void testDeleteCouponTemplate_notExists() {
        // 准备参数
        Long id = randomLongId();

        // 调用, 并断言异常
        assertServiceException(() -> couponTemplateService.deleteCouponTemplate(id), COUPON_TEMPLATE_NOT_EXISTS);
    }

    @Test
    public void testGetCouponTemplatePage() {
       // mock 数据
       CouponTemplateDO dbCouponTemplate = randomPojo(CouponTemplateDO.class, o -> { // 等会查询到
           o.setName("SpeedNet");
           o.setStatus(CommonStatusEnum.ENABLE.getStatus());
           o.setDiscountType(PromotionDiscountTypeEnum.PERCENT.getType());
           o.setCreateTime(buildTime(2022, 2, 2));
       });
       couponTemplateMapper.insert(dbCouponTemplate);
       // 测试 name 不匹配
       couponTemplateMapper.insert(cloneIgnoreId(dbCouponTemplate, o -> o.setName("土豆")));
       // 测试 status 不匹配
       couponTemplateMapper.insert(cloneIgnoreId(dbCouponTemplate, o -> o.setStatus(CommonStatusEnum.DISABLE.getStatus())));
       // 测试 type 不匹配
       couponTemplateMapper.insert(cloneIgnoreId(dbCouponTemplate, o -> o.setDiscountType(PromotionDiscountTypeEnum.PRICE.getType())));
       // 测试 createTime 不匹配
       couponTemplateMapper.insert(cloneIgnoreId(dbCouponTemplate, o -> o.setCreateTime(buildTime(2022, 1, 1))));
       // 准备参数
       CouponTemplatePageReqVO reqVO = new CouponTemplatePageReqVO();
       reqVO.setName("SpeedNet");
       reqVO.setStatus(CommonStatusEnum.ENABLE.getStatus());
       reqVO.setDiscountType(PromotionDiscountTypeEnum.PERCENT.getType());
       reqVO.setCreateTime((new LocalDateTime[]{buildTime(2022, 2, 1), buildTime(2022, 2, 3)}));

       // 调用
       PageResult<CouponTemplateDO> pageResult = couponTemplateService.getCouponTemplatePage(reqVO);
       // 断言
       assertEquals(1, pageResult.getTotal());
       assertEquals(1, pageResult.getList().size());
       assertPojoEquals(dbCouponTemplate, pageResult.getList().get(0));
    }

    @SafeVarargs
    private static CouponTemplateDO randomCouponTemplateDO(Consumer<CouponTemplateDO>... consumers) {
        Consumer<CouponTemplateDO> consumer = o -> {
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
            o.setTotalCount(10);
            o.setTakeLimitCount(1);
            o.setTakeType(CouponTakeTypeEnum.USER.getType());
            o.setUsePrice(0);
            o.setProductScope(PromotionProductScopeEnum.ALL.getScope());
            o.setProductScopeValues(null);
            o.setValidityType(CouponTemplateValidityTypeEnum.TERM.getType());
            o.setFixedStartTerm(0);
            o.setFixedEndTerm(7);
            o.setDiscountType(PromotionDiscountTypeEnum.PRICE.getType());
            o.setDiscountPrice(100);
            o.setTakeCount(0);
            o.setUseCount(0);
        };
        return randomPojo(CouponTemplateDO.class, ArrayUtils.append(consumer, consumers));
    }

}
