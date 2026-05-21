package team.starm.starmskyblock.permission;

public enum IslandPermission {

    // ====================== 管理权限 ======================
    ALL("所有权限"),
    DELETE_ISLAND("删除岛屿"),
    RENAME_ISLAND("修改岛屿名称"),
    EDIT_PERMISSIONS("修改岛屿权限"),
    EDIT_SETTINGS("修改岛屿设置"),
    INVITE_MEMBER("邀请成员"),
    REMOVE_MEMBER("移除成员"),
    SET_ROLE("设置成员角色"),
    INVITE_COOP("邀请合作者"),
    REMOVE_COOP("移除合作者"),
    SET_HOME("设置传送点"),
    SET_BIOME("设置生物群系"),

    // ====================== 物品丢弃/拾取 ======================
    ITEM_DROP("丢弃物品"),
    ITEM_PICKUP("拾取物品"),
    EXP_PICKUP("吸取经验球"),

    // ====================== 方块破坏/建造 ======================
    BREAK("破坏"),
    BUILD("建造"),

    // ====================== 工作方块 ======================
    CRAFTING_TABLE_USE("使用工作台"),
    ENCHANTING_TABLE_USE("使用附魔台"),
    BEACON_USE("使用信标"),
    ANVIL_USE("使用铁砧"),
    GRINDSTONE_USE("使用砂轮"),
    CARTOGRAPHY_TABLE_USE("使用制图台"),
    STONECUTTER_USE("使用切石机"),
    LOOM_USE("使用织布机"),
    SMITHING_TABLE_USE("使用锻造台"),
    CAMPFIRE_USE("使用营火"),

    // ====================== 容器======================
    FURNACE_OPEN("使用熔炉"),
    CHEST_OPEN("打开箱子"),
    BARREL_OPEN("打开木桶"),
    ENDER_CHEST_OPEN("打开末影箱"),
    SHULKER_BOX_OPEN("打开潜影盒"),
    HOPPER_OPEN("打开漏斗"),
    DISPENSER_OPEN("打开发射器"),
    DROPPER_OPEN("打开投掷器"),
    CRAFTER_OPEN("打开自动合成器"),
    BREWING_STAND_OPEN("打开酿造台"),
    SHELF_USE("使用展示架"),
    ITEM_FRAME_USE("使用物品展示框"),
    JUKEBOX_USE("使用唱片机"),
    LECTERN_USE("使用讲台"),
    CHISELED_BOOKSHELF_USE("使用雕纹书架"),
    DECORATED_POT_USE("使用陶罐"),
    COMPOSTER_USE("使用堆肥桶"),
    FLOWER_POT_USE("使用花盆"),
    ANIMAL_INVENTORY_OPEN("打开生物背包"),

    // ====================== 红石 ======================
    BUTTON_PRESS("按按钮"),
    LEVER_USE("拉拉杆"),
    REPEATER_USE("切换红石中继器"),
    COMPARATOR_USE("切换红石比较器"),
    DAYLIGHT_DETECTOR_USE("切换阳光探测器"),
    PRESSURE_PLATE_TRIGGER("触发压力板"),
    TRIPWIRE_HOOK_TRIGGER("触发绊线钩"),
    SCULK_SENSOR_TRIGGER("触发幽匿感测体"),
    BELL_RING("敲击钟"),
    NOTE_BLOCK_USE("使用音符盒"),

    // ====================== 门 ======================
    DOOR_OPEN("开关门"),
    FENCE_GATE_OPEN("开关栅栏门"),
    TRAPDOOR_OPEN("开关活板门"),

    // ====================== 载具 ======================
    MINECART_DAMAGE("破坏矿车"),
    MINECART_ENTER("乘坐矿车"),
    MINECART_PLACE("放置矿车"),
    BOAT_DAMAGE("破坏船"),
    BOAT_ENTER("乘坐船"),
    BOAT_PLACE("放置船"),

    // ====================== 工具 ======================
    BOW_USE("使用弓/弩"),
    AXE_USE("使用斧"),
    SHOVEL_USE("使用锹"),
    HOE_USE("使用锄"),
    BUCKET_USE("使用桶"),
    GLASS_BOTTLE_USE("使用玻璃瓶"),
    BOWL_USE("使用碗"),
    FISHING_ROD_USE("钓鱼"),
    FLINT_AND_STEEL_USE("点火"),
    SHEARS_USE("使用剪刀"),
    BRUSH_USE("使用刷子"),
    LEASH_USE("使用拴绳"),

    // ====================== 物品 ======================
    FIREWORK_USE("使用烟花"),
    NAME_TAG_USE("使用命名牌"),
    POTION_THROW("投掷药水"),
    WATER_BOTTLE_USE("使用水瓶"),
    BONE_MEAL_USE("使用骨粉"),
    DYE_USE("使用染料"),
    INK_SAC_USE("使用墨囊"),
    HONEYCOMB_USE("涂蜡"),
    CHORUS_FRUIT_EAT("食用紫颂果"),
    ENDER_PEARL_USE("使用末影珍珠"),
    ENDER_EYE_USE("使用末影之眼"),
    WIND_CHARGE_USE("使用风弹"),
    SNOWBALL_THROW("丢雪球"),
    EGG_THROW("丢鸡蛋"),

    // ====================== 生物 ======================
    ANIMAL_FEED("喂食动物"),
    ENTITY_RIDE("骑乘生物"),
    ENTITY_EQUIP("装备生物"),
    ANIMAL_DAMAGE("攻击动物"),
    MONSTER_DAMAGE("攻击怪物"),
    VILLAGER_DAMAGE("攻击村民"),
    VILLAGER_TRADE("村民交易"),
    BARTERING("以物易物"),
    ALLAY_INTERACT("与悦灵交互"),
    ARMOR_STAND_DAMAGE("攻击盔甲架"),
    ARMOR_STAND_INTERACT("与盔甲架交互"),

    // ====================== 其它 ======================
    SPAWN_EGG_USE("使用刷怪蛋"),
    FARMLAND_TRAMPLE("踩踏耕地"),
    TURTLE_EGG_TRAMPLE("踩踏海龟蛋"),
    SWEET_BERRY_HARVEST("采摘浆果"),
    CAKE_EAT("食用蛋糕"),
    SIGN_EDIT("编辑告示牌"),
    BED_USE("睡觉"),
    RESPAWN_ANCHOR_USE("使用重生锚"),
    END_CRYSTAL_DAMAGE("破坏末地水晶"),
    RAID_TRIGGER("触发袭击");

    private final String displayName;

    IslandPermission(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
