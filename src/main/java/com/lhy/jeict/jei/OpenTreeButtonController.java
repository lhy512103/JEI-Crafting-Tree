package com.lhy.jeict.jei;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.inputs.IJeiUserInput;

/**
 * JEI 閰嶆柟椤典晶鏍忕殑銆屾墦寮€閰嶆柟鏍戙€嶇澶存寜閽€傚乏閿洿鎺ユ墦寮€閰嶆柟鏍戙€? *
 * <p>褰?AE2 Utility 宸插畨瑁呬笖鍏惰嚜韬?JEI 缂栫爜绠ご鍙鏃讹紝鏈寜閽殣钘忥紝閬垮厤鍑虹幇涓や釜绠ご锛? * AE2 Utility 绠ご涓嶅彲瑙侊紙濡傛湭寮€缁堢涓旇韩涓婃病鏈夋棤绾挎牱鏉跨粓绔級鏃讹紝鏈寜閽帴绠℃樉绀恒€? * AE2 Utility 渚ч€氳繃 {@code com.lhy.ae2utility.api.Ae2UtilityClientApi#isJeiPatternEncodingAvailable()}
 * 鎻愪緵涓庤嚜韬澶村悓婧愮殑鍙鎬у垽瀹氾紱鍙嶅皠澶辫触鎴栨棫鐗堟湰鏃犳 API 鏃舵寜銆屽彲鐢ㄣ€嶅鐞嗭紙淇濇寔闅愯棌锛? * 閬垮厤鍦?AE2 Utility 缂栫爜绠ご瀛樺湪鏃堕噸澶嶆樉绀猴級銆? */
public class OpenTreeButtonController implements IIconButtonController {
    private static final int BG_COLOR = 0x804545FF;
    private static final String AE2U_CLIENT_API = "com.lhy.ae2utility.api.Ae2UtilityClientApi";
    private static final String AE2U_QUERY_METHOD = "isJeiPatternEncodingAvailable";
    private static boolean ae2uQueryResolved;
    private static @org.jetbrains.annotations.Nullable Method ae2uQueryMethod;

    private final IRecipeLayoutDrawable<?> recipeLayout;

    private final IDrawable arrowIcon = new IDrawable() {
        @Override
        public int getWidth() {
            return 10;
        }

        @Override
        public int getHeight() {
            return 10;
        }

        @Override
        public void draw(GuiGraphics graphics, int x, int y) {
            graphics.fill(x - 1, y - 1, x + 11, y + 11, BG_COLOR);
            // 鑷粯鍚戜笂绠ご
            int cx = x + 5;
            graphics.fill(cx - 1, y + 1, cx + 1, y + 9, 0xFFFFFFFF);
            graphics.fill(cx - 3, y + 4, cx + 3, y + 6, 0xFFFFFFFF);
            graphics.fill(cx - 2, y + 2, cx + 2, y + 3, 0xFFFFFFFF);
        }
    };

    public OpenTreeButtonController(IRecipeLayoutDrawable<?> recipeLayout) {
        this.recipeLayout = recipeLayout;
    }

    /** AE2 Utility 缂栫爜绠ご褰撳墠鏄惁鍙锛涙帰娴嬪け璐ユ垨鏃х増鏈寜鍙澶勭悊锛岄伩鍏嶅弻绠ご銆?*/
    private static boolean ae2UtilityEncodeArrowVisible() {
        if (!ModList.get().isLoaded("ae2utility")) {
            return false;
        }
        if (!ae2uQueryResolved) {
            ae2uQueryResolved = true;
            try {
                Class<?> api = Class.forName(AE2U_CLIENT_API);
                ae2uQueryMethod = api.getMethod(AE2U_QUERY_METHOD);
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                ae2uQueryMethod = null;
            }
        }
        Method query = ae2uQueryMethod;
        if (query == null) {
            // 鏃х増 AE2 Utility 鏃犳鏌ヨ API锛氫繚鎸侀殣钘忥紝閬垮厤鍙岀澶淬€?            return true;
        }
        try {
            return Boolean.TRUE.equals(query.invoke(null));
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return true;
        }
    }

    @Override
    public void initState(IButtonState state) {
        state.setIcon(arrowIcon);
    }

    @Override
    public void updateState(IButtonState state) {
        boolean visible = !ae2UtilityEncodeArrowVisible();
        state.setVisible(visible);
        state.setActive(visible);
    }

    @Override
    public boolean onPress(IJeiUserInput input) {
        if (ae2UtilityEncodeArrowVisible()) {
            return false;
        }
        if (input.isSimulate()) {
            return true;
        }
        RecipeTreeOpenHelper.openFromLayout(recipeLayout, Minecraft.getInstance().screen);
        return true;
    }

    @Override
    public void getTooltips(ITooltipBuilder tooltip) {
        tooltip.add(Component.translatable("jei.tooltip.jeict.open_tree_button"));
    }
}