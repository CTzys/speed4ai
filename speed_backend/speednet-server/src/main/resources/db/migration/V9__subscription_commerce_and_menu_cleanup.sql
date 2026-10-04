-- Consolidated uncommitted migrations V9 through V19, in their original order.
-- For databases already migrated to V19, use consolidate-v9; do not rerun this SQL.

-- BEGIN V9__remove_ai_iot_im_mp_menus.sql
-- Disable AI, IoT, IM and MP, including all descendant menus.
CREATE TEMPORARY TABLE disabled_platform_menu_ids AS
WITH RECURSIVE menu_tree AS (
    SELECT id
    FROM system_menu
    WHERE parent_id = 0 AND path IN ('/ai', '/iot', '/im', '/mp')
    UNION ALL
    SELECT child.id
    FROM system_menu child
    JOIN menu_tree parent ON child.parent_id = parent.id
)
SELECT DISTINCT id FROM menu_tree;

DELETE role_menu
FROM system_role_menu role_menu
JOIN disabled_platform_menu_ids removed ON removed.id = role_menu.menu_id;

UPDATE system_menu menu
JOIN disabled_platform_menu_ids removed ON removed.id = menu.id
SET menu.deleted = b'1', menu.status = 1, menu.visible = b'0';

DROP TEMPORARY TABLE disabled_platform_menu_ids;
-- END V9__remove_ai_iot_im_mp_menus.sql

-- BEGIN V10__enable_mall_and_payment_schema.sql
-- Mall and payment schema matched to the current repository entities.
-- Creates missing tables only; preserves existing business data.
-- No demo orders, products, credentials or payment channels are imported.

-- ProductBrandDO.java
CREATE TABLE IF NOT EXISTS `product_brand` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(255) NOT NULL COMMENT '品牌名称',
    `pic_url` varchar(255) NOT NULL COMMENT '品牌图片',
    `sort` int DEFAULT '0' COMMENT '品牌排序',
    `description` varchar(1024) DEFAULT NULL COMMENT '品牌描述',
    `status` int DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ProductCategoryDO.java
CREATE TABLE IF NOT EXISTS `product_category` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `parent_id` bigint NOT NULL COMMENT '父分类编号',
    `name` varchar(255) NOT NULL COMMENT '分类名称',
    `pic_url` varchar(255) NOT NULL COMMENT '移动端分类图',
    `sort` int DEFAULT '0' COMMENT '分类排序',
    `status` int DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`tenant_id`, `parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ProductCommentDO.java
CREATE TABLE IF NOT EXISTS `product_comment` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint DEFAULT NULL,
    `user_nickname` varchar(512) DEFAULT NULL,
    `user_avatar` varchar(512) DEFAULT NULL,
    `anonymous` bit(1) DEFAULT NULL,
    `order_id` bigint DEFAULT NULL,
    `order_item_id` bigint DEFAULT NULL,
    `spu_id` bigint DEFAULT NULL,
    `spu_name` varchar(512) DEFAULT NULL,
    `sku_id` bigint DEFAULT NULL,
    `sku_pic_url` varchar(512) DEFAULT NULL,
    `sku_properties` text DEFAULT NULL,
    `visible` bit(1) DEFAULT NULL,
    `scores` int DEFAULT NULL,
    `description_scores` int DEFAULT NULL,
    `benefit_scores` int DEFAULT NULL,
    `content` text DEFAULT NULL,
    `pic_urls` text DEFAULT NULL,
    `reply_status` bit(1) DEFAULT NULL,
    `reply_user_id` bigint DEFAULT NULL,
    `reply_content` varchar(512) DEFAULT NULL,
    `reply_time` datetime DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`tenant_id`, `user_id`),
    KEY `idx_order_id` (`tenant_id`, `order_id`),
    KEY `idx_order_item_id` (`tenant_id`, `order_item_id`),
    KEY `idx_spu_id` (`tenant_id`, `spu_id`),
    KEY `idx_sku_id` (`tenant_id`, `sku_id`),
    KEY `idx_reply_user_id` (`tenant_id`, `reply_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ProductFavoriteDO.java
CREATE TABLE IF NOT EXISTS `product_favorite` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint DEFAULT NULL,
    `spu_id` bigint DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`tenant_id`, `user_id`),
    KEY `idx_spu_id` (`tenant_id`, `spu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ProductBrowseHistoryDO.java
CREATE TABLE IF NOT EXISTS `product_browse_history` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `spu_id` bigint DEFAULT NULL,
    `user_id` bigint DEFAULT NULL,
    `user_deleted` bit(1) DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_spu_id` (`tenant_id`, `spu_id`),
    KEY `idx_user_id` (`tenant_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ProductPropertyDO.java
CREATE TABLE IF NOT EXISTS `product_property` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(64) DEFAULT NULL COMMENT '规格名称',
    `remark` varchar(255) DEFAULT NULL COMMENT '备注',
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ProductPropertyValueDO.java
CREATE TABLE IF NOT EXISTS `product_property_value` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `property_id` bigint DEFAULT NULL COMMENT '规格键id',
    `name` varchar(128) DEFAULT NULL COMMENT '规格值名字',
    `remark` varchar(255) DEFAULT NULL COMMENT '备注',
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_property_id` (`tenant_id`, `property_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ProductSkuDO.java
CREATE TABLE IF NOT EXISTS `product_sku` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `spu_id` bigint NOT NULL COMMENT 'spu编号',
    `properties` text DEFAULT NULL,
    `price` int NOT NULL DEFAULT '-1' COMMENT '商品价格，单位：分',
    `market_price` int DEFAULT NULL COMMENT '市场价，单位：分',
    `cost_price` int NOT NULL DEFAULT '-1' COMMENT '成本价，单位： 分',
    `bar_code` varchar(64)  DEFAULT NULL COMMENT 'SKU 的条形码',
    `pic_url` varchar(256)  NOT NULL COMMENT '图片地址',
    `stock` int DEFAULT NULL COMMENT '库存',
    `weight` double DEFAULT NULL COMMENT '商品重量，单位：kg 千克',
    `volume` double DEFAULT NULL COMMENT '商品体积，单位：m^3 平米',
    `first_brokerage_price` int DEFAULT NULL,
    `second_brokerage_price` int DEFAULT NULL,
    `sales_count` int DEFAULT NULL COMMENT '商品销量',
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_spu_id` (`tenant_id`, `spu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ProductSpuDO.java
CREATE TABLE IF NOT EXISTS `product_spu` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(128) NOT NULL COMMENT '商品名称',
    `keyword` varchar(256) NOT NULL COMMENT '关键字',
    `introduction` varchar(256) NOT NULL COMMENT '商品简介',
    `description` text NOT NULL COMMENT '商品详情',
    `category_id` bigint NOT NULL COMMENT '商品分类编号',
    `brand_id` bigint DEFAULT NULL,
    `pic_url` varchar(256) NOT NULL COMMENT '商品封面图',
    `slider_pic_urls` text DEFAULT NULL,
    `sort` int NOT NULL DEFAULT '0' COMMENT '排序字段',
    `status` int DEFAULT NULL,
    `spec_type` bit(1) DEFAULT NULL,
    `price` int NOT NULL DEFAULT '-1' COMMENT '商品价格，单位使用：分',
    `market_price` int NOT NULL COMMENT '市场价，单位使用：分',
    `cost_price` int NOT NULL DEFAULT '-1' COMMENT '成本价，单位： 分',
    `stock` int NOT NULL DEFAULT '0' COMMENT '库存',
    `delivery_types` text DEFAULT NULL,
    `delivery_template_id` bigint NOT NULL COMMENT '物流配置模板编号',
    `give_integral` int NOT NULL COMMENT '赠送积分',
    `sub_commission_type` bit(1) DEFAULT NULL,
    `sales_count` int DEFAULT '0' COMMENT '商品销量',
    `virtual_sales_count` int DEFAULT '0' COMMENT '虚拟销量',
    `browse_count` int DEFAULT '0' COMMENT '商品点击量',
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_category_id` (`tenant_id`, `category_id`),
    KEY `idx_brand_id` (`tenant_id`, `brand_id`),
    KEY `idx_delivery_template_id` (`tenant_id`, `delivery_template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ArticleCategoryDO.java
CREATE TABLE IF NOT EXISTS `promotion_article_category` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(512)  NOT NULL,
    `pic_url` varchar(512),
    `status` int      NOT NULL,
    `sort` int      NOT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ArticleDO.java
CREATE TABLE IF NOT EXISTS `promotion_article` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `category_id` bigint   NOT NULL,
    `spu_id` bigint   NOT NULL,
    `title` varchar(512)  NOT NULL,
    `author` varchar(512),
    `pic_url` varchar(512)  NOT NULL,
    `introduction` varchar(512),
    `browse_count` int DEFAULT NULL,
    `sort` int      NOT NULL,
    `status` int      NOT NULL,
    `recommend_hot` bit(1) DEFAULT NULL,
    `recommend_banner` bit(1) DEFAULT NULL,
    `content` varchar(512)  NOT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_category_id` (`tenant_id`, `category_id`),
    KEY `idx_spu_id` (`tenant_id`, `spu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- BannerDO.java
CREATE TABLE IF NOT EXISTS `promotion_banner` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `title` varchar(512) DEFAULT NULL,
    `url` varchar(512) DEFAULT NULL,
    `pic_url` varchar(512) DEFAULT NULL,
    `sort` int DEFAULT NULL,
    `status` int DEFAULT NULL,
    `position` int DEFAULT NULL,
    `memo` varchar(512) DEFAULT NULL,
    `browse_count` int DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- BargainActivityDO.java
CREATE TABLE IF NOT EXISTS `promotion_bargain_activity` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(512) DEFAULT NULL,
    `start_time` datetime DEFAULT NULL,
    `end_time` datetime DEFAULT NULL,
    `status` int DEFAULT NULL,
    `spu_id` bigint DEFAULT NULL,
    `sku_id` bigint DEFAULT NULL,
    `bargain_first_price` int DEFAULT NULL,
    `bargain_min_price` int DEFAULT NULL,
    `stock` int DEFAULT NULL,
    `total_stock` int DEFAULT NULL,
    `help_max_count` int DEFAULT NULL,
    `bargain_count` int DEFAULT NULL,
    `total_limit_count` int DEFAULT NULL,
    `random_min_price` int DEFAULT NULL,
    `random_max_price` int DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_spu_id` (`tenant_id`, `spu_id`),
    KEY `idx_sku_id` (`tenant_id`, `sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- BargainHelpDO.java
CREATE TABLE IF NOT EXISTS `promotion_bargain_help` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `activity_id` bigint DEFAULT NULL,
    `record_id` bigint DEFAULT NULL,
    `user_id` bigint DEFAULT NULL,
    `reduce_price` int DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_activity_id` (`tenant_id`, `activity_id`),
    KEY `idx_record_id` (`tenant_id`, `record_id`),
    KEY `idx_user_id` (`tenant_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- BargainRecordDO.java
CREATE TABLE IF NOT EXISTS `promotion_bargain_record` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint DEFAULT NULL,
    `activity_id` bigint DEFAULT NULL,
    `spu_id` bigint DEFAULT NULL,
    `sku_id` bigint DEFAULT NULL,
    `bargain_first_price` int DEFAULT NULL,
    `bargain_price` int DEFAULT NULL,
    `status` int DEFAULT NULL,
    `end_time` datetime DEFAULT NULL,
    `order_id` bigint DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`tenant_id`, `user_id`),
    KEY `idx_activity_id` (`tenant_id`, `activity_id`),
    KEY `idx_spu_id` (`tenant_id`, `spu_id`),
    KEY `idx_sku_id` (`tenant_id`, `sku_id`),
    KEY `idx_order_id` (`tenant_id`, `order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- CombinationActivityDO.java
CREATE TABLE IF NOT EXISTS `promotion_combination_activity` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(512)  NOT NULL,
    `spu_id` bigint,
    `total_limit_count` int      NOT NULL,
    `single_limit_count` int      NOT NULL,
    `start_time` datetime DEFAULT NULL,
    `end_time` datetime DEFAULT NULL,
    `user_size` int      NOT NULL,
    `virtual_group` bit(1) DEFAULT NULL,
    `status` int      NOT NULL,
    `limit_duration` int      NOT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_spu_id` (`tenant_id`, `spu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- CombinationProductDO.java
CREATE TABLE IF NOT EXISTS `promotion_combination_product` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `activity_id` bigint DEFAULT NULL,
    `spu_id` bigint DEFAULT NULL,
    `sku_id` bigint DEFAULT NULL,
    `combination_price` int DEFAULT NULL,
    `activity_status` int DEFAULT NULL,
    `activity_start_time` datetime DEFAULT NULL,
    `activity_end_time` datetime DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_activity_id` (`tenant_id`, `activity_id`),
    KEY `idx_spu_id` (`tenant_id`, `spu_id`),
    KEY `idx_sku_id` (`tenant_id`, `sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- CombinationRecordDO.java
CREATE TABLE IF NOT EXISTS `promotion_combination_record` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `activity_id` bigint   NOT NULL,
    `combination_price` int      NOT NULL,
    `spu_id` bigint   NOT NULL,
    `spu_name` varchar(512)  NOT NULL,
    `pic_url` varchar(512),
    `sku_id` bigint   NOT NULL,
    `count` int      NOT NULL,
    `user_id` bigint   NOT NULL,
    `nickname` varchar(512),
    `avatar` varchar(512),
    `head_id` bigint   NOT NULL,
    `status` int      NOT NULL,
    `order_id` bigint   NOT NULL,
    `user_size` int      NOT NULL,
    `user_count` int      NOT NULL,
    `virtual_group` bit(1) DEFAULT NULL,
    `expire_time` datetime,
    `start_time` datetime,
    `end_time` datetime,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_activity_id` (`tenant_id`, `activity_id`),
    KEY `idx_spu_id` (`tenant_id`, `spu_id`),
    KEY `idx_sku_id` (`tenant_id`, `sku_id`),
    KEY `idx_user_id` (`tenant_id`, `user_id`),
    KEY `idx_head_id` (`tenant_id`, `head_id`),
    KEY `idx_order_id` (`tenant_id`, `order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- CouponDO.java
CREATE TABLE IF NOT EXISTS `promotion_coupon` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `template_id` bigint   NOT NULL,
    `name` varchar(512)  NOT NULL,
    `status` int      NOT NULL,
    `user_id` bigint   NOT NULL,
    `take_type` int      NOT NULL,
    `use_price` int      NOT NULL,
    `valid_start_time` datetime NOT NULL,
    `valid_end_time` datetime NOT NULL,
    `product_scope` int      NOT NULL,
    `product_scope_values` text DEFAULT NULL,
    `discount_type` int      NOT NULL,
    `discount_percent` int,
    `discount_price` int,
    `discount_limit_price` int,
    `use_order_id` bigint,
    `use_time` datetime,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_template_id` (`tenant_id`, `template_id`),
    KEY `idx_user_id` (`tenant_id`, `user_id`),
    KEY `idx_use_order_id` (`tenant_id`, `use_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- CouponTemplateDO.java
CREATE TABLE IF NOT EXISTS `promotion_coupon_template` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(512)  NOT NULL,
    `description` varchar(512),
    `status` int      NOT NULL,
    `total_count` int      NOT NULL,
    `take_limit_count` int      NOT NULL,
    `take_type` int      NOT NULL,
    `use_price` int      NOT NULL,
    `product_scope` int      NOT NULL,
    `product_scope_values` text DEFAULT NULL,
    `validity_type` int      NOT NULL,
    `valid_start_time` datetime,
    `valid_end_time` datetime,
    `fixed_start_term` int,
    `fixed_end_term` int,
    `discount_type` int      NOT NULL,
    `discount_percent` int,
    `discount_price` int,
    `discount_limit_price` int,
    `take_count` int      NOT NULL DEFAULT 0,
    `use_count` int      NOT NULL DEFAULT 0,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- DiscountActivityDO.java
CREATE TABLE IF NOT EXISTS `promotion_discount_activity` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(512)  NOT NULL,
    `status` int      NOT NULL,
    `start_time` datetime NOT NULL,
    `end_time` datetime NOT NULL,
    `remark` varchar(512),
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- DiscountProductDO.java
CREATE TABLE IF NOT EXISTS `promotion_discount_product` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `activity_id` bigint   NOT NULL,
    `spu_id` bigint   NOT NULL,
    `sku_id` bigint   NOT NULL,
    `discount_type` int      NOT NULL,
    `discount_percent` int,
    `discount_price` int,
    `activity_name` varchar(512)  NOT NULL,
    `activity_status` int      NOT NULL,
    `activity_start_time` datetime NOT NULL,
    `activity_end_time` datetime NOT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_activity_id` (`tenant_id`, `activity_id`),
    KEY `idx_spu_id` (`tenant_id`, `spu_id`),
    KEY `idx_sku_id` (`tenant_id`, `sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- DiyPageDO.java
CREATE TABLE IF NOT EXISTS `promotion_diy_page` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `template_id` bigint   NOT NULL,
    `name` varchar(512)  NOT NULL,
    `remark` varchar(512),
    `preview_pic_urls` text DEFAULT NULL,
    `property` varchar(512),
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_template_id` (`tenant_id`, `template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- DiyTemplateDO.java
CREATE TABLE IF NOT EXISTS `promotion_diy_template` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(512)  NOT NULL,
    `used` bit(1) DEFAULT NULL,
    `used_time` datetime DEFAULT NULL,
    `remark` varchar(512),
    `preview_pic_urls` text DEFAULT NULL,
    `property` varchar(512)  NOT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- KeFuConversationDO.java
CREATE TABLE IF NOT EXISTS `promotion_kefu_conversation` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint DEFAULT NULL,
    `last_message_time` datetime DEFAULT NULL,
    `last_message_content` varchar(512) DEFAULT NULL,
    `last_message_content_type` int DEFAULT NULL,
    `admin_pinned` bit(1) DEFAULT NULL,
    `user_deleted` bit(1) DEFAULT NULL,
    `admin_deleted` bit(1) DEFAULT NULL,
    `admin_unread_message_count` int DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`tenant_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- KeFuMessageDO.java
CREATE TABLE IF NOT EXISTS `promotion_kefu_message` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `conversation_id` bigint DEFAULT NULL,
    `sender_id` bigint DEFAULT NULL,
    `sender_type` int DEFAULT NULL,
    `receiver_id` bigint DEFAULT NULL,
    `receiver_type` int DEFAULT NULL,
    `content_type` int DEFAULT NULL,
    `content` text DEFAULT NULL,
    `read_status` bit(1) DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_conversation_id` (`tenant_id`, `conversation_id`),
    KEY `idx_sender_id` (`tenant_id`, `sender_id`),
    KEY `idx_receiver_id` (`tenant_id`, `receiver_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- PointActivityDO.java
CREATE TABLE IF NOT EXISTS `promotion_point_activity` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `spu_id` bigint DEFAULT NULL,
    `status` int DEFAULT NULL,
    `remark` varchar(512) DEFAULT NULL,
    `sort` int DEFAULT NULL,
    `stock` int DEFAULT NULL,
    `total_stock` int DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_spu_id` (`tenant_id`, `spu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- PointProductDO.java
CREATE TABLE IF NOT EXISTS `promotion_point_product` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `activity_id` bigint DEFAULT NULL,
    `spu_id` bigint DEFAULT NULL,
    `sku_id` bigint DEFAULT NULL,
    `count` int DEFAULT NULL,
    `point` int DEFAULT NULL,
    `price` int DEFAULT NULL,
    `stock` int DEFAULT NULL,
    `activity_status` int DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_activity_id` (`tenant_id`, `activity_id`),
    KEY `idx_spu_id` (`tenant_id`, `spu_id`),
    KEY `idx_sku_id` (`tenant_id`, `sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- RewardActivityDO.java
CREATE TABLE IF NOT EXISTS `promotion_reward_activity` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(512)  NOT NULL,
    `status` int      NOT NULL,
    `start_time` datetime NOT NULL,
    `end_time` datetime NOT NULL,
    `remark` varchar(512),
    `condition_type` int      NOT NULL,
    `product_scope` int      NOT NULL,
    `product_scope_values` text DEFAULT NULL,
    `rules` text DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- SeckillActivityDO.java
CREATE TABLE IF NOT EXISTS `promotion_seckill_activity` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `spu_id` bigint   NOT NULL,
    `name` varchar(512)  NOT NULL,
    `status` int      NOT NULL,
    `remark` varchar(512),
    `start_time` datetime DEFAULT NULL,
    `end_time` datetime DEFAULT NULL,
    `sort` int      NOT NULL,
    `config_ids` text DEFAULT NULL,
    `total_limit_count` int,
    `single_limit_count` int,
    `stock` int,
    `total_stock` int,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_spu_id` (`tenant_id`, `spu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- SeckillConfigDO.java
CREATE TABLE IF NOT EXISTS `promotion_seckill_config` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(512)  NOT NULL,
    `start_time` varchar(512)  NOT NULL,
    `end_time` varchar(512)  NOT NULL,
    `slider_pic_urls` text DEFAULT NULL,
    `status` int      NOT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- SeckillProductDO.java
CREATE TABLE IF NOT EXISTS `promotion_seckill_product` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `activity_id` bigint DEFAULT NULL,
    `config_ids` text DEFAULT NULL,
    `spu_id` bigint DEFAULT NULL,
    `sku_id` bigint DEFAULT NULL,
    `seckill_price` int DEFAULT NULL,
    `stock` int DEFAULT NULL,
    `activity_status` int DEFAULT NULL,
    `activity_start_time` datetime DEFAULT NULL,
    `activity_end_time` datetime DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_activity_id` (`tenant_id`, `activity_id`),
    KEY `idx_spu_id` (`tenant_id`, `spu_id`),
    KEY `idx_sku_id` (`tenant_id`, `sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ProductStatisticsDO.java
CREATE TABLE IF NOT EXISTS `product_statistics` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `time` date DEFAULT NULL,
    `spu_id` bigint DEFAULT NULL,
    `browse_count` int DEFAULT NULL,
    `browse_user_count` int DEFAULT NULL,
    `favorite_count` int DEFAULT NULL,
    `cart_count` int DEFAULT NULL,
    `order_count` int DEFAULT NULL,
    `order_pay_count` int DEFAULT NULL,
    `order_pay_price` int DEFAULT NULL,
    `after_sale_count` int DEFAULT NULL,
    `after_sale_refund_price` int DEFAULT NULL,
    `browse_convert_percent` int DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_spu_id` (`tenant_id`, `spu_id`),
    UNIQUE KEY `uk_business` (`tenant_id`, `spu_id`, `time`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- TradeStatisticsDO.java
CREATE TABLE IF NOT EXISTS `trade_statistics` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `time` datetime DEFAULT NULL,
    `order_create_count` int DEFAULT NULL,
    `order_pay_count` int DEFAULT NULL,
    `order_pay_price` int DEFAULT NULL,
    `after_sale_count` int DEFAULT NULL,
    `after_sale_refund_price` int DEFAULT NULL,
    `brokerage_settlement_price` int DEFAULT NULL,
    `wallet_pay_price` int DEFAULT NULL,
    `recharge_pay_count` int DEFAULT NULL,
    `recharge_pay_price` int DEFAULT NULL,
    `recharge_refund_count` int DEFAULT NULL,
    `recharge_refund_price` int DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_business` (`tenant_id`, `time`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- AfterSaleDO.java
CREATE TABLE IF NOT EXISTS `trade_after_sale` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `no` varchar(512)  NOT NULL,
    `status` int      NOT NULL,
    `way` int      NOT NULL,
    `type` int      NOT NULL,
    `user_id` bigint   NOT NULL,
    `apply_reason` varchar(512)  NOT NULL,
    `apply_description` varchar(512),
    `apply_pic_urls` text DEFAULT NULL,
    `order_id` bigint   NOT NULL,
    `order_no` varchar(512)  NOT NULL,
    `order_item_id` bigint   NOT NULL,
    `spu_id` bigint   NOT NULL,
    `spu_name` varchar(512)  NOT NULL,
    `sku_id` bigint   NOT NULL,
    `properties` text DEFAULT NULL,
    `pic_url` varchar(512),
    `count` int      NOT NULL,
    `audit_time` datetime DEFAULT NULL,
    `audit_user_id` bigint,
    `audit_reason` varchar(512),
    `refund_price` int      NOT NULL,
    `pay_refund_id` bigint,
    `refund_time` datetime DEFAULT NULL,
    `logistics_id` bigint,
    `logistics_no` varchar(512),
    `delivery_time` datetime DEFAULT NULL,
    `receive_time` datetime DEFAULT NULL,
    `receive_reason` varchar(512),
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`tenant_id`, `user_id`),
    KEY `idx_order_id` (`tenant_id`, `order_id`),
    KEY `idx_order_item_id` (`tenant_id`, `order_item_id`),
    KEY `idx_spu_id` (`tenant_id`, `spu_id`),
    KEY `idx_sku_id` (`tenant_id`, `sku_id`),
    KEY `idx_audit_user_id` (`tenant_id`, `audit_user_id`),
    KEY `idx_pay_refund_id` (`tenant_id`, `pay_refund_id`),
    KEY `idx_logistics_id` (`tenant_id`, `logistics_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- AfterSaleLogDO.java
CREATE TABLE IF NOT EXISTS `trade_after_sale_log` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint   NOT NULL,
    `user_type` int      NOT NULL,
    `after_sale_id` bigint   NOT NULL,
    `before_status` int,
    `after_status` int      NOT NULL,
    `operate_type` int DEFAULT NULL,
    `content` varchar(512)  NOT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`tenant_id`, `user_id`),
    KEY `idx_after_sale_id` (`tenant_id`, `after_sale_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- BrokerageRecordDO.java
CREATE TABLE IF NOT EXISTS `trade_brokerage_record` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint   NOT NULL,
    `biz_id` varchar(128) NOT NULL,
    `biz_type` int DEFAULT NULL,
    `title` varchar(512)  NOT NULL,
    `description` varchar(512)  NOT NULL,
    `price` int      NOT NULL,
    `total_price` int      NOT NULL,
    `status` int DEFAULT NULL,
    `frozen_days` int      NOT NULL,
    `unfreeze_time` datetime DEFAULT NULL,
    `source_user_level` int,
    `source_user_id` bigint,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`tenant_id`, `user_id`),
    KEY `idx_biz_id` (`tenant_id`, `biz_id`),
    KEY `idx_source_user_id` (`tenant_id`, `source_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- BrokerageUserDO.java
CREATE TABLE IF NOT EXISTS `trade_brokerage_user` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `bind_user_id` bigint   NOT NULL,
    `bind_user_time` datetime DEFAULT NULL,
    `brokerage_enabled` bit(1) DEFAULT NULL,
    `brokerage_time` datetime DEFAULT NULL,
    `brokerage_price` int DEFAULT NULL,
    `frozen_price` int      NOT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_bind_user_id` (`tenant_id`, `bind_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- BrokerageWithdrawDO.java
CREATE TABLE IF NOT EXISTS `trade_brokerage_withdraw` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint   NOT NULL,
    `price` int      NOT NULL,
    `fee_price` int      NOT NULL,
    `total_price` int      NOT NULL,
    `type` int DEFAULT NULL,
    `user_name` varchar(512) DEFAULT NULL,
    `user_account` varchar(512) DEFAULT NULL,
    `qr_code_url` varchar(512) DEFAULT NULL,
    `bank_name` varchar(512),
    `bank_address` varchar(512),
    `status` int DEFAULT NULL,
    `audit_reason` varchar(512),
    `audit_time` datetime DEFAULT NULL,
    `remark` varchar(512),
    `pay_transfer_id` bigint DEFAULT NULL,
    `transfer_channel_code` varchar(512) DEFAULT NULL,
    `transfer_time` datetime DEFAULT NULL,
    `transfer_error_msg` varchar(512) DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`tenant_id`, `user_id`),
    KEY `idx_pay_transfer_id` (`tenant_id`, `pay_transfer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- CartDO.java
CREATE TABLE IF NOT EXISTS `trade_cart` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint DEFAULT NULL,
    `spu_id` bigint DEFAULT NULL,
    `sku_id` bigint DEFAULT NULL,
    `count` int DEFAULT NULL,
    `selected` bit(1) DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`tenant_id`, `user_id`),
    KEY `idx_spu_id` (`tenant_id`, `spu_id`),
    KEY `idx_sku_id` (`tenant_id`, `sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- TradeConfigDO.java
CREATE TABLE IF NOT EXISTS `trade_config` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `after_sale_refund_reasons` text DEFAULT NULL,
    `after_sale_return_reasons` text DEFAULT NULL,
    `delivery_express_free_enabled` bit(1) DEFAULT NULL,
    `delivery_express_free_price` int DEFAULT NULL,
    `delivery_pick_up_enabled` bit(1) DEFAULT NULL,
    `brokerage_enabled` bit(1) DEFAULT NULL,
    `brokerage_enabled_condition` int DEFAULT NULL,
    `brokerage_bind_mode` int DEFAULT NULL,
    `brokerage_poster_urls` text DEFAULT NULL,
    `brokerage_first_percent` int DEFAULT NULL,
    `brokerage_second_percent` int DEFAULT NULL,
    `brokerage_withdraw_min_price` int DEFAULT NULL,
    `brokerage_withdraw_fee_percent` int DEFAULT NULL,
    `brokerage_frozen_days` int DEFAULT NULL,
    `brokerage_withdraw_types` text DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- DeliveryExpressDO.java
CREATE TABLE IF NOT EXISTS `trade_delivery_express` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `code` varchar(128) NOT NULL,
    `name` varchar(512),
    `logo` varchar(512)  NULL,
    `sort` int      NOT NULL,
    `status` int      NOT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- DeliveryExpressTemplateChargeDO.java
CREATE TABLE IF NOT EXISTS `trade_delivery_express_template_charge` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `template_id` bigint DEFAULT NULL,
    `area_ids` text DEFAULT NULL,
    `charge_mode` int DEFAULT NULL,
    `start_count` double DEFAULT NULL,
    `start_price` int DEFAULT NULL,
    `extra_count` double DEFAULT NULL,
    `extra_price` int DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_template_id` (`tenant_id`, `template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- DeliveryExpressTemplateDO.java
CREATE TABLE IF NOT EXISTS `trade_delivery_express_template` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(512) DEFAULT NULL,
    `charge_mode` int DEFAULT NULL,
    `sort` int DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- DeliveryExpressTemplateFreeDO.java
CREATE TABLE IF NOT EXISTS `trade_delivery_express_template_free` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `template_id` bigint DEFAULT NULL,
    `area_ids` text DEFAULT NULL,
    `free_price` int DEFAULT NULL,
    `free_count` int DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_template_id` (`tenant_id`, `template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- DeliveryPickUpStoreDO.java
CREATE TABLE IF NOT EXISTS `trade_delivery_pick_up_store` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(512) DEFAULT NULL,
    `introduction` varchar(512) DEFAULT NULL,
    `phone` varchar(512) DEFAULT NULL,
    `area_id` int DEFAULT NULL,
    `detail_address` varchar(512) DEFAULT NULL,
    `logo` varchar(512) DEFAULT NULL,
    `opening_time` time DEFAULT NULL,
    `closing_time` time DEFAULT NULL,
    `latitude` double DEFAULT NULL,
    `longitude` double DEFAULT NULL,
    `verify_user_ids` text DEFAULT NULL,
    `status` int DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_area_id` (`tenant_id`, `area_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- TradeOrderDO.java
CREATE TABLE IF NOT EXISTS `trade_order` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `no` varchar(512)  NOT NULL,
    `type` int      NOT NULL,
    `terminal` int      NOT NULL,
    `user_id` bigint   NOT NULL,
    `user_ip` varchar(512)  NOT NULL,
    `user_remark` varchar(512),
    `status` int      NOT NULL,
    `product_count` int      NOT NULL,
    `finish_time` datetime,
    `cancel_time` datetime,
    `cancel_type` int,
    `remark` varchar(512),
    `comment_status` bit(1) DEFAULT NULL,
    `brokerage_user_id` bigint,
    `pay_order_id` bigint,
    `pay_status` bit(1) DEFAULT NULL,
    `pay_time` datetime,
    `pay_channel_code` varchar(512),
    `total_price` int      NULL,
    `discount_price` int      NOT NULL,
    `delivery_price` int      NOT NULL,
    `adjust_price` int      NOT NULL,
    `pay_price` int      NOT NULL,
    `delivery_type` int      NOT NULL,
    `logistics_id` bigint,
    `logistics_no` varchar(512),
    `delivery_time` datetime,
    `receive_time` datetime,
    `receiver_name` varchar(512)  NOT NULL,
    `receiver_mobile` varchar(512)  NOT NULL,
    `receiver_area_id` int      NOT NULL,
    `receiver_detail_address` varchar(512)  NOT NULL,
    `pick_up_store_id` bigint DEFAULT NULL,
    `pick_up_verify_code` varchar(512)  NULL,
    `refund_status` int      NULL,
    `refund_price` int      NULL,
    `coupon_id` bigint   NOT NULL,
    `coupon_price` int      NOT NULL,
    `use_point` int      NULL,
    `point_price` int      NOT NULL,
    `give_point` int      NULL,
    `refund_point` int      NULL,
    `vip_price` int      NULL,
    `give_coupon_template_counts` text DEFAULT NULL,
    `give_coupon_ids` text DEFAULT NULL,
    `seckill_activity_id` bigint DEFAULT NULL,
    `bargain_activity_id` bigint DEFAULT NULL,
    `bargain_record_id` bigint DEFAULT NULL,
    `combination_activity_id` bigint DEFAULT NULL,
    `combination_head_id` bigint DEFAULT NULL,
    `combination_record_id` bigint DEFAULT NULL,
    `point_activity_id` bigint DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`tenant_id`, `user_id`),
    KEY `idx_brokerage_user_id` (`tenant_id`, `brokerage_user_id`),
    KEY `idx_pay_order_id` (`tenant_id`, `pay_order_id`),
    KEY `idx_logistics_id` (`tenant_id`, `logistics_id`),
    KEY `idx_receiver_area_id` (`tenant_id`, `receiver_area_id`),
    KEY `idx_pick_up_store_id` (`tenant_id`, `pick_up_store_id`),
    KEY `idx_coupon_id` (`tenant_id`, `coupon_id`),
    KEY `idx_seckill_activity_id` (`tenant_id`, `seckill_activity_id`),
    KEY `idx_bargain_activity_id` (`tenant_id`, `bargain_activity_id`),
    KEY `idx_bargain_record_id` (`tenant_id`, `bargain_record_id`),
    KEY `idx_combination_activity_id` (`tenant_id`, `combination_activity_id`),
    KEY `idx_combination_head_id` (`tenant_id`, `combination_head_id`),
    KEY `idx_combination_record_id` (`tenant_id`, `combination_record_id`),
    KEY `idx_point_activity_id` (`tenant_id`, `point_activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- TradeOrderItemDO.java
CREATE TABLE IF NOT EXISTS `trade_order_item` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint   NOT NULL,
    `order_id` bigint   NOT NULL,
    `cart_id` bigint DEFAULT NULL,
    `spu_id` bigint   NOT NULL,
    `spu_name` varchar(512)  NOT NULL,
    `sku_id` bigint   NOT NULL,
    `properties` text DEFAULT NULL,
    `pic_url` varchar(512),
    `count` int      NOT NULL,
    `comment_status` bit(1) DEFAULT NULL,
    `price` int      NOT NULL,
    `discount_price` int      NOT NULL,
    `delivery_price` int      NULL,
    `adjust_price` int      NULL,
    `pay_price` int      NOT NULL,
    `coupon_price` int      NULL,
    `point_price` int      NULL,
    `use_point` int      NULL,
    `give_point` int      NULL,
    `vip_price` int      NULL,
    `after_sale_id` bigint DEFAULT NULL,
    `after_sale_status` int      NOT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`tenant_id`, `user_id`),
    KEY `idx_order_id` (`tenant_id`, `order_id`),
    KEY `idx_cart_id` (`tenant_id`, `cart_id`),
    KEY `idx_spu_id` (`tenant_id`, `spu_id`),
    KEY `idx_sku_id` (`tenant_id`, `sku_id`),
    KEY `idx_after_sale_id` (`tenant_id`, `after_sale_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- TradeOrderLogDO.java
CREATE TABLE IF NOT EXISTS `trade_order_log` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint DEFAULT NULL,
    `user_type` int DEFAULT NULL,
    `order_id` bigint DEFAULT NULL,
    `before_status` int DEFAULT NULL,
    `after_status` int DEFAULT NULL,
    `operate_type` int DEFAULT NULL,
    `content` text DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`tenant_id`, `user_id`),
    KEY `idx_order_id` (`tenant_id`, `order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- PayAppDO.java
CREATE TABLE IF NOT EXISTS `pay_app` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `app_key` varchar(128) NOT NULL,
    `name` varchar(64)   NOT NULL,
    `status` int DEFAULT NULL,
    `remark` varchar(255)           DEFAULT NULL,
    `order_notify_url` varchar(1024) NOT NULL,
    `refund_notify_url` varchar(1024) NOT NULL,
    `transfer_notify_url` varchar(512) DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_app_key` (`tenant_id`, `app_key`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- PayChannelDO.java
CREATE TABLE IF NOT EXISTS `pay_channel` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `code` varchar(128) NOT NULL,
    `status` int DEFAULT NULL,
    `fee_rate` double         NOT NULL DEFAULT 0,
    `remark` varchar(255)            DEFAULT NULL,
    `app_id` bigint(20)     NOT NULL,
    `config` text DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_app_id` (`tenant_id`, `app_id`),
    KEY `idx_app_code` (`tenant_id`, `app_id`, `code`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- PayDemoOrderDO.java
CREATE TABLE IF NOT EXISTS `pay_demo_order` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint DEFAULT NULL,
    `spu_id` bigint DEFAULT NULL,
    `spu_name` varchar(512) DEFAULT NULL,
    `price` int DEFAULT NULL,
    `pay_status` bit(1) DEFAULT NULL,
    `pay_order_id` bigint DEFAULT NULL,
    `pay_time` datetime DEFAULT NULL,
    `pay_channel_code` varchar(512) DEFAULT NULL,
    `pay_refund_id` bigint DEFAULT NULL,
    `refund_price` int DEFAULT NULL,
    `refund_time` datetime DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`tenant_id`, `user_id`),
    KEY `idx_spu_id` (`tenant_id`, `spu_id`),
    KEY `idx_pay_order_id` (`tenant_id`, `pay_order_id`),
    KEY `idx_pay_refund_id` (`tenant_id`, `pay_refund_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- PayDemoWithdrawDO.java
CREATE TABLE IF NOT EXISTS `pay_demo_withdraw` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `subject` varchar(512) DEFAULT NULL,
    `price` int DEFAULT NULL,
    `user_account` varchar(512) DEFAULT NULL,
    `user_name` varchar(512) DEFAULT NULL,
    `type` int DEFAULT NULL,
    `status` int DEFAULT NULL,
    `pay_transfer_id` bigint DEFAULT NULL,
    `transfer_channel_code` varchar(512) DEFAULT NULL,
    `transfer_time` datetime DEFAULT NULL,
    `transfer_error_msg` varchar(512) DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_pay_transfer_id` (`tenant_id`, `pay_transfer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- PayNotifyLogDO.java
CREATE TABLE IF NOT EXISTS `pay_notify_log` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `task_id` bigint(20)    NOT NULL,
    `notify_times` int    NOT NULL,
    `response` varchar(1024) NOT NULL,
    `status` int DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_task_id` (`tenant_id`, `task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- PayNotifyTaskDO.java
CREATE TABLE IF NOT EXISTS `pay_notify_task` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `app_id` bigint(20)    NOT NULL,
    `type` int DEFAULT NULL,
    `data_id` bigint(20)    NOT NULL,
    `merchant_order_id` varchar(128) NOT NULL,
    `merchant_refund_id` varchar(128) NOT NULL,
    `merchant_transfer_id` varchar(128) NOT NULL,
    `status` int DEFAULT NULL,
    `next_notify_time` datetime(0)   NULL     DEFAULT NULL,
    `last_execute_time` datetime(0)   NULL     DEFAULT NULL,
    `notify_times` int    NOT NULL,
    `max_notify_times` int    NOT NULL,
    `notify_url` varchar(1024) NOT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_app_id` (`tenant_id`, `app_id`),
    KEY `idx_data_id` (`tenant_id`, `data_id`),
    KEY `idx_merchant_order_id` (`tenant_id`, `merchant_order_id`),
    KEY `idx_merchant_refund_id` (`tenant_id`, `merchant_refund_id`),
    KEY `idx_merchant_transfer_id` (`tenant_id`, `merchant_transfer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- PayOrderDO.java
CREATE TABLE IF NOT EXISTS `pay_order` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `app_id` bigint(20)    NOT NULL,
    `channel_id` bigint(20)             DEFAULT NULL,
    `channel_code` varchar(32)            DEFAULT NULL,
    `user_id` bigint(20)             DEFAULT NULL,
    `user_type` int DEFAULT NULL,
    `merchant_order_id` varchar(128) NOT NULL,
    `subject` varchar(32)   NOT NULL,
    `body` varchar(128)  NOT NULL,
    `notify_url` varchar(1024) NOT NULL,
    `price` int DEFAULT NULL,
    `channel_fee_rate` double                 DEFAULT 0,
    `channel_fee_price` int DEFAULT NULL,
    `status` int DEFAULT NULL,
    `user_ip` varchar(50)   NOT NULL,
    `expire_time` datetime DEFAULT NULL,
    `success_time` datetime(0)            DEFAULT CURRENT_TIMESTAMP,
    `extension_id` bigint(20)             DEFAULT NULL,
    `no` varchar(64)   NULL,
    `refund_price` int DEFAULT NULL,
    `channel_user_id` varchar(128) NOT NULL,
    `channel_order_no` varchar(64)            DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_app_id` (`tenant_id`, `app_id`),
    KEY `idx_channel_id` (`tenant_id`, `channel_id`),
    KEY `idx_user_id` (`tenant_id`, `user_id`),
    KEY `idx_merchant_order_id` (`tenant_id`, `merchant_order_id`),
    KEY `idx_extension_id` (`tenant_id`, `extension_id`),
    KEY `idx_channel_user_id` (`tenant_id`, `channel_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- PayOrderExtensionDO.java
CREATE TABLE IF NOT EXISTS `pay_order_extension` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `no` varchar(64)         NOT NULL,
    `order_id` bigint(20)    NOT NULL,
    `channel_id` bigint(20)    NOT NULL,
    `channel_code` varchar(32)   NOT NULL,
    `user_ip` varchar(50)   NULL     DEFAULT NULL,
    `status` int DEFAULT NULL,
    `channel_extras` text DEFAULT NULL,
    `channel_error_code` varchar(64)  NULL,
    `channel_error_msg` varchar(64)    NULL,
    `channel_notify_data` varchar(1024)  NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`tenant_id`, `order_id`),
    KEY `idx_channel_id` (`tenant_id`, `channel_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- PayRefundDO.java
CREATE TABLE IF NOT EXISTS `pay_refund` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `no` varchar(64)         NOT NULL,
    `app_id` bigint(20)    NOT NULL,
    `channel_id` bigint(20)    NOT NULL,
    `channel_code` varchar(32)   NOT NULL,
    `order_id` bigint(20)    NOT NULL,
    `order_no` varchar(64)    NOT NULL,
    `user_id` bigint(20)    NULL     DEFAULT NULL,
    `user_type` int DEFAULT NULL,
    `merchant_order_id` varchar(128) NOT NULL,
    `merchant_refund_id` varchar(128) NOT NULL,
    `notify_url` varchar(1024) NOT NULL,
    `status` int DEFAULT NULL,
    `pay_price` int DEFAULT NULL,
    `refund_price` int DEFAULT NULL,
    `reason` varchar(256)  NOT NULL,
    `user_ip` varchar(50)   NULL     DEFAULT NULL,
    `channel_order_no` varchar(64)   NOT NULL,
    `channel_refund_no` varchar(64)   NULL     DEFAULT NULL,
    `success_time` datetime(0)   NULL     DEFAULT NULL,
    `channel_error_code` varchar(128)  NULL     DEFAULT NULL,
    `channel_error_msg` varchar(256)  NULL     DEFAULT NULL,
    `channel_notify_data` varchar(1024)  NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_app_id` (`tenant_id`, `app_id`),
    KEY `idx_channel_id` (`tenant_id`, `channel_id`),
    KEY `idx_order_id` (`tenant_id`, `order_id`),
    KEY `idx_user_id` (`tenant_id`, `user_id`),
    KEY `idx_merchant_order_id` (`tenant_id`, `merchant_order_id`),
    KEY `idx_merchant_refund_id` (`tenant_id`, `merchant_refund_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- PayTransferDO.java
CREATE TABLE IF NOT EXISTS `pay_transfer` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `no` varchar(64)   NOT NULL,
    `app_id` bigint(20)    NOT NULL,
    `channel_id` bigint(20)    NOT NULL,
    `channel_code` varchar(32)   NOT NULL,
    `user_id` bigint(20)    NULL     DEFAULT NULL,
    `user_type` int DEFAULT NULL,
    `merchant_transfer_id` varchar(128) NOT NULL,
    `subject` varchar(256)  NOT NULL,
    `price` int DEFAULT NULL,
    `user_account` varchar(256)  NOT NULL,
    `user_name` varchar(64)   NULL     DEFAULT NULL,
    `status` int DEFAULT NULL,
    `success_time` datetime(0)   NULL     DEFAULT NULL,
    `notify_url` varchar(1024) NULL     DEFAULT NULL,
    `user_ip` varchar(50)   NULL     DEFAULT NULL,
    `channel_extras` text DEFAULT NULL,
    `channel_transfer_no` varchar(64)   NULL     DEFAULT NULL,
    `channel_error_code` varchar(128)  NULL     DEFAULT NULL,
    `channel_error_msg` varchar(256)  NULL     DEFAULT NULL,
    `channel_notify_data` varchar(1024) NULL     DEFAULT NULL,
    `channel_package_info` varchar(1024) NULL     DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_app_id` (`tenant_id`, `app_id`),
    KEY `idx_channel_id` (`tenant_id`, `channel_id`),
    KEY `idx_user_id` (`tenant_id`, `user_id`),
    KEY `idx_merchant_transfer_id` (`tenant_id`, `merchant_transfer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- PayWalletDO.java
CREATE TABLE IF NOT EXISTS `pay_wallet` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint DEFAULT NULL,
    `user_type` int DEFAULT NULL,
    `balance` int DEFAULT NULL,
    `freeze_price` int DEFAULT NULL,
    `total_expense` int DEFAULT NULL,
    `total_recharge` int DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`tenant_id`, `user_id`),
    UNIQUE KEY `uk_business` (`tenant_id`, `user_id`, `user_type`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- PayWalletRechargeDO.java
CREATE TABLE IF NOT EXISTS `pay_wallet_recharge` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `wallet_id` bigint DEFAULT NULL,
    `total_price` int DEFAULT NULL,
    `pay_price` int DEFAULT NULL,
    `bonus_price` int DEFAULT NULL,
    `package_id` bigint DEFAULT NULL,
    `pay_status` bit(1) DEFAULT NULL,
    `pay_order_id` bigint DEFAULT NULL,
    `pay_channel_code` varchar(512) DEFAULT NULL,
    `pay_time` datetime DEFAULT NULL,
    `pay_refund_id` bigint DEFAULT NULL,
    `refund_total_price` int DEFAULT NULL,
    `refund_pay_price` int DEFAULT NULL,
    `refund_bonus_price` int DEFAULT NULL,
    `refund_time` datetime DEFAULT NULL,
    `refund_status` int DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_wallet_id` (`tenant_id`, `wallet_id`),
    KEY `idx_package_id` (`tenant_id`, `package_id`),
    KEY `idx_pay_order_id` (`tenant_id`, `pay_order_id`),
    KEY `idx_pay_refund_id` (`tenant_id`, `pay_refund_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- PayWalletRechargePackageDO.java
CREATE TABLE IF NOT EXISTS `pay_wallet_recharge_package` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(512) DEFAULT NULL,
    `pay_price` int DEFAULT NULL,
    `bonus_price` int DEFAULT NULL,
    `status` int DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- PayWalletTransactionDO.java
CREATE TABLE IF NOT EXISTS `pay_wallet_transaction` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `no` varchar(512) DEFAULT NULL,
    `wallet_id` bigint DEFAULT NULL,
    `biz_type` int DEFAULT NULL,
    `biz_id` varchar(128) NOT NULL,
    `title` varchar(512) DEFAULT NULL,
    `price` int DEFAULT NULL,
    `balance` int DEFAULT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_wallet_id` (`tenant_id`, `wallet_id`),
    KEY `idx_biz_id` (`tenant_id`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Start with conservative trading defaults for the default tenant.
INSERT INTO trade_config (after_sale_refund_reasons, after_sale_return_reasons, delivery_express_free_enabled, delivery_express_free_price, delivery_pick_up_enabled, brokerage_enabled, brokerage_enabled_condition, brokerage_bind_mode, brokerage_poster_urls, brokerage_first_percent, brokerage_second_percent, brokerage_withdraw_min_price, brokerage_withdraw_fee_percent, brokerage_frozen_days, brokerage_withdraw_types, tenant_id)
SELECT '[]', '[]', b'0', 0, b'0', b'0', 1, 1, '[]', 0, 0, 0, 0, 0, '', 1
WHERE NOT EXISTS (SELECT 1 FROM trade_config WHERE tenant_id = 1 AND deleted = 0);
-- END V10__enable_mall_and_payment_schema.sql

-- BEGIN V11__subscription_products.sql
-- Subscription products have their own billing and fulfilment lifecycle.
CREATE TABLE subscription_plan (
 id bigint PRIMARY KEY AUTO_INCREMENT, tenant_id bigint NOT NULL, name varchar(128) NOT NULL,
 description text, level int NOT NULL DEFAULT 1, visible bit NOT NULL DEFAULT 0,
 enabled bit NOT NULL DEFAULT 0, renew_enabled bit NOT NULL DEFAULT 1,
 total_bytes bigint NOT NULL, reset_mode varchar(16) NOT NULL DEFAULT 'monthly',
 reset_interval_days int NOT NULL DEFAULT 30, traffic_mode varchar(16) NOT NULL DEFAULT 'both',
 node_limit int NOT NULL DEFAULT 1, capacity_limit int NOT NULL DEFAULT 0, sort int NOT NULL DEFAULT 0, version int NOT NULL DEFAULT 1,
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 KEY idx_tenant (tenant_id,sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE subscription_plan_price (
 id bigint PRIMARY KEY AUTO_INCREMENT, tenant_id bigint NOT NULL, plan_id bigint NOT NULL,
 name varchar(64) NOT NULL, months int NOT NULL DEFAULT 1, price int NOT NULL,
 kind varchar(16) NOT NULL DEFAULT 'period', bytes bigint NOT NULL DEFAULT 0, enabled bit NOT NULL DEFAULT 1,
 KEY idx_plan (tenant_id,plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE subscription_plan_node (
 id bigint PRIMARY KEY AUTO_INCREMENT, tenant_id bigint NOT NULL, plan_id bigint NOT NULL,
 node_id bigint NOT NULL, server_id bigint NOT NULL, inbound_id bigint NOT NULL, public_host varchar(255) NOT NULL,
 UNIQUE KEY uk_node (tenant_id,plan_id,node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE subscription_account (
 id bigint PRIMARY KEY AUTO_INCREMENT, tenant_id bigint NOT NULL, user_id bigint NOT NULL,
 current_subscription_id bigint NULL, version bigint NOT NULL DEFAULT 0, conflict bit NOT NULL DEFAULT 0,
 UNIQUE KEY uk_user (tenant_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO subscription_account (tenant_id,user_id,current_subscription_id,conflict)
SELECT tenant_id,user_id,CASE WHEN COUNT(*)=1 THEN MAX(id) ELSE NULL END,COUNT(*)>1 FROM subscription
WHERE deleted=0 AND ended_time IS NULL GROUP BY tenant_id,user_id;
ALTER TABLE subscription ADD COLUMN plan_id bigint NULL,
 ADD COLUMN base_total_bytes bigint NULL, ADD COLUMN extra_used_bytes bigint NOT NULL DEFAULT 0;
CREATE TABLE subscription_purchase (
 id bigint PRIMARY KEY AUTO_INCREMENT, tenant_id bigint NOT NULL, user_id bigint NOT NULL,
 number varchar(64) NOT NULL, request_key varchar(64) NOT NULL, subscription_id bigint NULL,
 plan_id bigint NOT NULL, plan_version int NOT NULL, price_id bigint NOT NULL,
 plan_name varchar(128) NOT NULL, kind varchar(16) NOT NULL, months int NOT NULL,
 bytes bigint NOT NULL, base_bytes bigint NOT NULL, reset_mode varchar(16) NOT NULL,
 reset_interval_days int NOT NULL, traffic_mode varchar(16) NOT NULL, node_limit int NOT NULL,
 nodes_json text NOT NULL, original_amount int NOT NULL, credit_amount int NOT NULL DEFAULT 0,
 amount int NOT NULL, account_version bigint NOT NULL, pay_order_id bigint NULL,
 status varchar(24) NOT NULL DEFAULT 'pending', error varchar(500) NOT NULL DEFAULT '',
 expires_at datetime NOT NULL, paid_at datetime NULL, service_start datetime NULL, service_end datetime NULL,
 credited bit NOT NULL DEFAULT 0, create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_request (tenant_id,user_id,request_key), UNIQUE KEY uk_number (number),
 KEY idx_user (tenant_id,user_id,id), KEY idx_pending (tenant_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE subscription_traffic_pack (
 id bigint PRIMARY KEY AUTO_INCREMENT, tenant_id bigint NOT NULL, subscription_id bigint NOT NULL,
 purchase_id bigint NOT NULL, total_bytes bigint NOT NULL, remaining_bytes bigint NOT NULL,
 UNIQUE KEY uk_purchase (tenant_id,purchase_id), KEY idx_subscription (tenant_id,subscription_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT '套餐商品','',2,0,id,'plans','ep:goods','subscription/plan/index','SubscriptionPlans',0,1,1,1,'1','1',0
FROM system_menu WHERE path='/subscription' AND parent_id=0 AND deleted=0;
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT '套餐管理','subscription:plan:manage',3,1,id,'','','',0,1,1,1,'1','1',0
FROM system_menu WHERE component_name='SubscriptionPlans' AND deleted=0;
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT '套餐订单','',2,1,id,'orders','ep:tickets','subscription/order/index','SubscriptionOrders',0,1,1,1,'1','1',0
FROM system_menu WHERE path='/subscription' AND parent_id=0 AND deleted=0;
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT '套餐订单管理','subscription:order:manage',3,1,id,'','','',0,1,1,1,'1','1',0
FROM system_menu WHERE component_name='SubscriptionOrders' AND deleted=0;

-- Separate payment application; configure actual channel credentials in payment management.
INSERT INTO pay_app (tenant_id,app_key,name,status,remark,order_notify_url,refund_notify_url,transfer_notify_url)
SELECT id,'subscription','订阅套餐',0,'请配置当前后端的公网地址及支付渠道','http://127.0.0.1:48080/app-api/subscription-products/notify','','' FROM system_tenant WHERE deleted=0
AND NOT EXISTS (SELECT 1 FROM pay_app WHERE pay_app.tenant_id=system_tenant.id AND app_key='subscription' AND deleted=0);
-- END V11__subscription_products.sql

-- BEGIN V12__payment_optional_channel_fields.sql
-- A waiting order has no channel user or successful payment timestamp yet.
ALTER TABLE pay_order MODIFY channel_user_id varchar(128) NULL DEFAULT NULL,
 MODIFY success_time datetime NULL DEFAULT NULL;
-- Notifications use one business identifier depending on payment/refund/transfer type.
ALTER TABLE pay_notify_task MODIFY merchant_order_id varchar(128) NULL DEFAULT NULL,
 MODIFY merchant_refund_id varchar(128) NULL DEFAULT NULL,
 MODIFY merchant_transfer_id varchar(128) NULL DEFAULT NULL;
-- END V12__payment_optional_channel_fields.sql

-- BEGIN V13__mall_system_audit_fields.sql
-- Background payment notifications and scheduled business jobs have no interactive user.
-- Match BaseDO audit fields: null is valid when a system task performs the operation.
ALTER TABLE `product_brand` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `product_category` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `product_comment` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `product_favorite` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `product_browse_history` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `product_property` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `product_property_value` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `product_sku` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `product_spu` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `promotion_article_category` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `promotion_article` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `promotion_banner` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `promotion_bargain_activity` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `promotion_bargain_help` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `promotion_bargain_record` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `promotion_combination_activity` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `promotion_combination_product` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `promotion_combination_record` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `promotion_coupon` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `promotion_coupon_template` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `promotion_discount_activity` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `promotion_discount_product` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `promotion_diy_page` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `promotion_diy_template` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `promotion_kefu_conversation` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `promotion_kefu_message` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `promotion_point_activity` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `promotion_point_product` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `promotion_reward_activity` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `promotion_seckill_activity` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `promotion_seckill_config` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `promotion_seckill_product` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `product_statistics` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `trade_statistics` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `trade_after_sale` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `trade_after_sale_log` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `trade_brokerage_record` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `trade_brokerage_user` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `trade_brokerage_withdraw` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `trade_cart` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `trade_config` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `trade_delivery_express` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `trade_delivery_express_template_charge` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `trade_delivery_express_template` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `trade_delivery_express_template_free` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `trade_delivery_pick_up_store` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `trade_order` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `trade_order_item` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `trade_order_log` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `pay_app` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `pay_channel` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `pay_demo_order` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `pay_demo_withdraw` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `pay_notify_log` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `pay_notify_task` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `pay_order` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `pay_order_extension` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `pay_refund` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `pay_transfer` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `pay_wallet` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `pay_wallet_recharge` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `pay_wallet_recharge_package` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
ALTER TABLE `pay_wallet_transaction` MODIFY creator varchar(64) NULL DEFAULT '', MODIFY updater varchar(64) NULL DEFAULT '';
-- END V13__mall_system_audit_fields.sql

-- BEGIN V14__subscription_payment_retry_fairness.sql
-- Rotate payment checks so persistent failures do not starve later orders.
ALTER TABLE subscription_purchase ADD COLUMN last_checked_at datetime NOT NULL DEFAULT '2000-01-01 00:00:00';
CREATE INDEX idx_payment_check ON subscription_purchase(status,last_checked_at,id);
-- END V14__subscription_payment_retry_fairness.sql

-- BEGIN V15__optional_product_fields_and_node_groups.sql
CREATE TABLE subscription_node_group (
 id bigint PRIMARY KEY AUTO_INCREMENT,tenant_id bigint NOT NULL,name varchar(128) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE subscription_node_group_binding (
 id bigint PRIMARY KEY AUTO_INCREMENT,tenant_id bigint NOT NULL,group_id bigint NOT NULL,
 node_id bigint NOT NULL,server_id bigint NOT NULL,inbound_id bigint NOT NULL,public_host varchar(255) NOT NULL,
 UNIQUE KEY uk_group_node(tenant_id,group_id,node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
ALTER TABLE subscription_plan ADD COLUMN group_id bigint NULL;

-- Optional merchandising fields must not prevent saving subscription-style goods.
ALTER TABLE product_spu
 MODIFY COLUMN keyword varchar(256) NULL DEFAULT NULL,
 MODIFY COLUMN introduction varchar(256) NULL DEFAULT NULL,
 MODIFY COLUMN description text NULL,
 MODIFY COLUMN category_id bigint NULL DEFAULT NULL,
 MODIFY COLUMN pic_url varchar(256) NULL DEFAULT NULL,
 MODIFY COLUMN market_price int NULL DEFAULT NULL,
 MODIFY COLUMN cost_price int NULL DEFAULT NULL,
 MODIFY COLUMN delivery_template_id bigint NULL DEFAULT NULL;
ALTER TABLE product_sku
 MODIFY COLUMN pic_url varchar(256) NULL DEFAULT NULL,
 MODIFY COLUMN cost_price int NULL DEFAULT NULL;
-- END V15__optional_product_fields_and_node_groups.sql

-- BEGIN V16__subscription_product_admin_menus.sql
-- Preserve existing product records; the product entry now manages subscription plans.
UPDATE system_menu SET name='订阅商品', update_time=NOW()
WHERE component='mall/product/spu/index' AND deleted=0;
UPDATE system_menu SET visible=0, update_time=NOW()
WHERE component IN ('mall/product/category/index','mall/product/brand/index',
'mall/product/property/index','mall/product/comment/index') AND deleted=0;
-- END V16__subscription_product_admin_menus.sql

-- BEGIN V17__subscription_multiple_inbound_clients.sql
-- Preserve an assignment per client when one exit node has multiple entry points.
ALTER TABLE xray_node_assignment
  DROP INDEX uk_node_assignment,
  ADD UNIQUE KEY uk_assignment_client (tenant_id, client_id);
-- END V17__subscription_multiple_inbound_clients.sql

-- BEGIN V18__hide_report_workflow_cms_menus.sql
-- Hide optional root menus while retaining routes and role permissions.
UPDATE system_menu
SET visible = b'0', update_time = NOW()
WHERE parent_id = 0 AND path IN ('/report', '/bpm', '/cms') AND deleted = b'0';
-- END V18__hide_report_workflow_cms_menus.sql

-- BEGIN V19__hide_legacy_mall_menus.sql
-- Keep member analytics available outside the retired mall navigation.
-- Preserve business data, routes and role permissions for historical access.
UPDATE system_menu statistics
JOIN system_menu member_root
  ON member_root.parent_id = 0 AND member_root.path = '/member'
  AND member_root.deleted = b'0'
SET statistics.parent_id = member_root.id,
    statistics.path = 'statistics', statistics.visible = b'1',
    statistics.update_time = NOW()
WHERE statistics.component = 'mall/statistics/member/index'
  AND statistics.deleted = b'0';

CREATE TEMPORARY TABLE legacy_mall_menu_ids AS
WITH RECURSIVE menu_tree AS (
    SELECT id FROM system_menu
    WHERE parent_id = 0 AND path = '/mall'
    UNION ALL
    SELECT child.id FROM system_menu child
    JOIN menu_tree parent ON child.parent_id = parent.id
)
SELECT DISTINCT id FROM menu_tree;

UPDATE system_menu menu
JOIN legacy_mall_menu_ids legacy ON legacy.id = menu.id
SET menu.visible = b'0', menu.update_time = NOW()
WHERE menu.deleted = b'0' AND menu.type IN (1, 2);

DROP TEMPORARY TABLE legacy_mall_menu_ids;
-- END V19__hide_legacy_mall_menus.sql
