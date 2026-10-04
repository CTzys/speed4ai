package com.speednet.module.product.controller.admin.spu.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 商品 SPU 新增/更新 Request VO")
@Data
public class ProductSpuSaveReqVO {

    @Schema(description = "商品编号", example = "1")
    private Long id;

    @Schema(description = "商品名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "清凉小短袖")
    @NotBlank(message = "商品名称不能为空")
    private String name;

    @Schema(description = "关键字", example = "清凉丝滑不出汗")
    @JsonSetter(nulls = Nulls.SKIP)
    private String keyword = "";

    @Schema(description = "商品简介", example = "清凉小短袖简介")
    @JsonSetter(nulls = Nulls.SKIP)
    private String introduction = "";

    @Schema(description = "商品详情", example = "清凉小短袖详情")
    @JsonSetter(nulls = Nulls.SKIP)
    private String description = "";

    @Schema(description = "商品分类编号", example = "1")
    private Long categoryId;

    @Schema(description = "商品品牌编号", example = "1")
    private Long brandId;

    @Schema(description = "商品封面图", example = "https://www.speednet.local/xx.png")
    @JsonSetter(nulls = Nulls.SKIP)
    private String picUrl = "";

    @Schema(description = "商品轮播图", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "[https://www.speednet.local/xx.png, https://www.speednet.local/xxx.png]")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private List<String> sliderPicUrls = new java.util.ArrayList<>();

    @Schema(description = "排序字段", example = "1")
    @JsonSetter(nulls = Nulls.SKIP)
    private Integer sort = 0;

    // ========== SKU 相关字段 =========

    @Schema(description = "规格类型", example = "true")
    @JsonSetter(nulls = Nulls.SKIP)
    private Boolean specType = false;

    // ========== 物流相关字段 =========

    @Schema(description = "配送方式数组", example = "1")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private List<Integer> deliveryTypes = new java.util.ArrayList<>();

    @Schema(description = "物流配置模板编号", example = "111")
    private Long deliveryTemplateId;

    // ========== 营销相关字段 =========

    @Schema(description = "赠送积分", example = "111")
    @JsonSetter(nulls = Nulls.SKIP)
    private Integer giveIntegral = 0;

    @Schema(description = "分销类型", example = "true")
    @JsonSetter(nulls = Nulls.SKIP)
    private Boolean subCommissionType = false;

    // ========== 统计相关字段 =========

    @Schema(description = "虚拟销量", example = "66")
    @JsonSetter(nulls = Nulls.SKIP)
    private Integer virtualSalesCount = 0;

    @Schema(description = "商品销量", example = "1999")
    private Integer salesCount;

    @Schema(description = "浏览量", example = "1999")
    private Integer browseCount;

    // ========== SKU 相关字段 =========

    @Schema(description = "SKU 数组")
    @Valid
    @NotEmpty(message = "至少配置一种商品售价和库存")
    private List<ProductSkuSaveReqVO> skus;

}
