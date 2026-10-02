package com.hhy.dreamingfishcore.gameplay.research_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.block.DreamingFishCore_Blocks;
import com.hhy.dreamingfishcore.gameplay.blueprint_system.BlueprintConfig;
import com.hhy.dreamingfishcore.gameplay.blueprint_system.PlayerBlueprintData;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 研究桌的端到端 gametest：**放入物品 → 提交 → 扣掉正确数量 → 解锁蓝图**。
 *
 * <p>覆盖的是真实链路：右键方块开容器菜单（原版 {@code openMenu}）→ 往槽位里放物品 →
 * 客户端那种"我点了提交"的调用（{@link ResearchService#handleSubmit}）→ 服务端重算并扣物品、解锁。
 * 顺带钉住三条不能退让的规则：数量不足不扣、已经学会不扣、免蓝图物品不扣；
 * 以及"关菜单时槽里剩下的必须还给玩家"——吞物品是这块最容易踩的坑。</p>
 *
 * <p><b>配置</b>：gametest 服务器与开发客户端共用 {@code run/config/dreamingfishcore} 下的文件，
 * 所以这里先备份、临时改成确定值（蓝图启用、提交除数 4），结束时原样恢复。
 * 不这么做的话，测试结果会取决于服主本地的配置（例如蓝图系统默认是关的，整个功能都用不了）。</p>
 */
@GameTestHolder(DreamingFishCore.MODID)
@PrefixGameTestTemplate(false)
public class ResearchGameTest {

    /** 测试用的临时配置：蓝图全开（没有任何免蓝图物品），提交除数 4。 */
    private static final String TEST_BLUEPRINT_JSON = """
            {
              "schemaVersion": 1,
              "enabled": true,
              "siegeZombieDropPercent": 5.0,
              "chestDropPercent": 25.0,
              "defaultUnlockedItems": [],
              "exemptNamespaces": [],
              "blueprintWhitelist": [],
              "blueprintBlacklist": []
            }
            """;

    private static final String TEST_RESEARCH_JSON = """
            {
              "schemaVersion": 1,
              "enabled": true,
              "costExperiencePoints": 100,
              "minRecipes": 2,
              "maxRecipes": 3,
              "namespaces": [],
              "skipLearned": true,
              "submitDivisor": 4
            }
            """;

    @GameTest(template = "empty")
    public static void submittingAQuarterStackUnlocksTheBlueprint(GameTestHelper helper) {
        Path blueprintPath = BlueprintConfig.getConfigPath();
        Path researchPath = ResearchTableConfig.getConfigPath();
        byte[] blueprintBackup = readOrNull(blueprintPath);
        byte[] researchBackup = readOrNull(researchPath);
        ServerPlayer player = null;

        try {
            write(researchPath, TEST_RESEARCH_JSON);
            write(blueprintPath, TEST_BLUEPRINT_JSON);
            ResearchTableConfig.reload();
            BlueprintConfig.reload();
            PlayerBlueprintData.markPoolDirty();

            List<String> pool = PlayerBlueprintData.getBlueprintPool();
            helper.assertTrue(!pool.isEmpty(),
                    "gametest 服务器应当加载出非空的蓝图抽取池（说明配方已加载、蓝图已临时启用）");
            String itemId = firstStackableOf64(pool);
            helper.assertTrue(itemId != null,
                    "抽取池里应当有堆叠上限 64 的物品，否则测不出「四分之一组」这条规则");
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
            int required = ResearchMath.requiredSubmitCount(new ItemStack(item).getMaxStackSize(),
                    ResearchTableConfig.current().getSubmitDivisor());
            helper.assertTrue(required == 16, "64 堆叠的物品应当交 16 个，实际 " + required);

            // 摆一张研究桌，并把模拟玩家挪到旁边（交互有距离判定）。
            helper.setBlock(new BlockPos(1, 1, 1), DreamingFishCore_Blocks.RESEARCH_TABLE.get());
            BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
            player = helper.makeMockServerPlayerInLevel();
            AuthSessionGuard.markAuthenticated(player);
            player.teleportTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);

            // 右键开界面在无头环境里走不通：原版 openMenu 会给我们这张带额外数据的菜单发
            // neoforge:advanced_open_screen，而模拟玩家的连接没有协商过任何自定义通道，
            // NetworkRegistry.checkPacket 会直接抛 "may not be sent to the client!"。
            // 所以这里直接构造菜单并挂到玩家身上——等同 openMenu 里
            // initMenu(menu) + containerMenu = menu 那两步。真实客户端上的开屏路径
            // （openMenu → MenuScreens → Screen_ResearchTable）只能在游戏里人工验证。
            ResearchTableMenu menu = new ResearchTableMenu(101, player.getInventory(), pos);
            player.containerMenu = menu;
            helper.assertTrue(menu.matches(pos), "菜单应当记住自己对应哪张研究桌");

            // ---- 数量不足：必须被拒，且一个都不许扣 ----
            menu.getSlot(ResearchTableMenu.SUBMIT_SLOT).set(new ItemStack(item, required - 1));
            ResearchService.handleSubmit(player, pos);
            helper.assertTrue(menu.getSubmitStack().getCount() == required - 1,
                    "数量不足时不能扣物品，实际剩 " + menu.getSubmitStack().getCount());
            helper.assertFalse(PlayerBlueprintData.hasLearned(player, itemId),
                    "数量不足时不能解锁");
            String missing = ResearchService.submitState(player, pos, ResearchTableConfig.current()).status();
            helper.assertTrue(missing.contains("需要") && missing.contains(String.valueOf(required)),
                    "数量不足的提示要说清需要几个，实际：" + missing);

            // ---- 数量刚好够（并且多放了 3 个）：扣 16 留 3，蓝图解锁 ----
            menu.getSlot(ResearchTableMenu.SUBMIT_SLOT).set(new ItemStack(item, required + 3));
            ResearchService.handleSubmit(player, pos);
            helper.assertTrue(menu.getSubmitStack().getCount() == 3,
                    "应当只扣掉 " + required + " 个、剩下的留在槽里，实际剩 " + menu.getSubmitStack().getCount());
            helper.assertTrue(PlayerBlueprintData.hasLearned(player, itemId),
                    "提交之后应当解锁 " + itemId);

            // ---- 已经学会：不能再扣 ----
            menu.getSlot(ResearchTableMenu.SUBMIT_SLOT).set(new ItemStack(item, 5));
            ResearchService.handleSubmit(player, pos);
            helper.assertTrue(menu.getSubmitStack().getCount() == 5,
                    "已经学会时不能再扣物品，实际剩 " + menu.getSubmitStack().getCount());
            String learned = ResearchService.submitState(player, pos, ResearchTableConfig.current()).status();
            helper.assertTrue(learned.contains("已经学会"), "已经学会要给明确提示，实际：" + learned);

            // ---- 不在抽取池里的物品：明确说"该物品不需要蓝图"，且不扣 ----
            menu.getSlot(ResearchTableMenu.SUBMIT_SLOT).set(new ItemStack(Items.DIRT, 64));
            ResearchService.handleSubmit(player, pos);
            helper.assertTrue(menu.getSubmitStack().getCount() == 64,
                    "免蓝图的物品不能被扣，实际剩 " + menu.getSubmitStack().getCount());
            String noBlueprint = ResearchService.submitState(player, pos, ResearchTableConfig.current()).status();
            helper.assertTrue(noBlueprint.contains("不需要蓝图"),
                    "不在抽取池里应当提示该物品不需要蓝图，实际：" + noBlueprint);

            // ---- 原有的"花经验研究一批配方"必须继续可用 ----
            menu.getSlot(ResearchTableMenu.SUBMIT_SLOT).set(ItemStack.EMPTY);
            player.giveExperiencePoints(2000);
            int learnedBefore = PlayerBlueprintData.getLearnedBlueprintItems(player).size();
            int experienceBefore = ResearchService.experiencePoints(player);
            ResearchService.handleConfirm(player, pos);
            helper.assertTrue(PlayerBlueprintData.getLearnedBlueprintItems(player).size() > learnedBefore,
                    "花经验研究应当至少学会一个配方");
            helper.assertTrue(ResearchService.experiencePoints(player) < experienceBefore,
                    "花经验研究应当扣掉经验");

            // ---- 关闭菜单：槽里剩下的必须回到背包，绝不能吞 ----
            menu.getSlot(ResearchTableMenu.SUBMIT_SLOT).set(new ItemStack(item, 7));
            player.doCloseContainer();
            helper.assertTrue(!(player.containerMenu instanceof ResearchTableMenu),
                    "关菜单之后玩家不该还挂着一张研究桌菜单");
            helper.assertTrue(countInInventory(player, item) == 7,
                    "关菜单时槽里剩下的 7 个必须还给玩家，实际背包里有 " + countInInventory(player, item) + " 个");
        } finally {
            if (player != null && player.containerMenu instanceof ResearchTableMenu) {
                // 断言中途失败时也要把还给玩家这一步跑完，避免物品留在菜单里影响其它测试。
                player.doCloseContainer();
            }
            restore(researchPath, researchBackup);
            restore(blueprintPath, blueprintBackup);
            // 恢复成磁盘上的真实配置，后面的测试（以及开发者本机）才不会被这里改动的值污染。
            ResearchTableConfig.reload();
            BlueprintConfig.reload();
            PlayerBlueprintData.markPoolDirty();
            if (player != null) {
                // 把模拟玩家从服务器上摘掉：它会一直留在世界里，而附近的其它 gametest
                // （感染系统就靠"附近有没有玩家"来判定）不该被一个测试残留的旁观者影响。
                helper.getLevel().getServer().getPlayerList().remove(player);
            }
        }
        helper.succeed();
    }

    /** 从抽取池里挑一个"堆叠上限 64"的物品，挑不到返回 null。 */
    private static String firstStackableOf64(List<String> pool) {
        for (String itemId : pool) {
            ResourceLocation key = ResourceLocation.tryParse(itemId);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(key);
            if (item != Items.AIR && new ItemStack(item).getMaxStackSize() == 64) {
                return itemId;
            }
        }
        return null;
    }

    /** 玩家背包（主背包 36 格）里这件物品的总数。 */
    private static int countInInventory(ServerPlayer player, Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.getItem() == item) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static byte[] readOrNull(Path path) {
        try {
            return Files.exists(path) ? Files.readAllBytes(path) : null;
        } catch (IOException exception) {
            return null;
        }
    }

    private static void write(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            // 写不进去的话下面的 reload 会读到旧配置，断言会带着实际值失败——
            // 不在这里抛，是为了让失败信息是"抽取池是空的"而不是一个栈。
            DreamingFishCore.LOGGER.error("gametest 写入配置失败：{}", path, exception);
        }
    }

    private static void restore(Path path, byte[] backup) {
        try {
            if (backup == null) {
                Files.deleteIfExists(path);
            } else {
                Files.write(path, backup);
            }
        } catch (IOException exception) {
            DreamingFishCore.LOGGER.error("gametest 恢复配置失败：{}", path, exception);
        }
    }
}
