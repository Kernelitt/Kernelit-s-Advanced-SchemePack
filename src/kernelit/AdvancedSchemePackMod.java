package kernelit;

import arc.Core;
import arc.struct.Seq;
import arc.struct.StringMap;
import arc.files.Fi;
import arc.util.Log;
import arc.scene.ui.layout.Table;
import mindustry.Vars;
import mindustry.game.Schematic;
import mindustry.game.Schematics;
import mindustry.mod.Mod;
import mindustry.mod.Mods.LoadedMod;
import arc.struct.ObjectMap;
import mindustry.world.blocks.storage.CoreBlock;

import java.lang.reflect.Method;

public class AdvancedSchemePackMod extends Mod {
    @Override
    public void init() {
        String currentMode = Core.settings.getString("kernelit-schem-mode", "campaign");
        reloadSchematics(currentMode);

        Vars.ui.settings.addCategory("Advanced Scheme Pack", (String) null, table -> {
            table.button("", () -> {
                String nextMode = Core.settings.getString("kernelit-schem-mode", "campaign").equals("campaign") ? "pvp" : "campaign";
                Core.settings.put("kernelit-schem-mode", nextMode);
                reloadSchematics(nextMode);
                refreshSchematicsDialog();
            }).width(280f).update(b -> b.setText("Режим схем: " + getModeName())).row();
        });
    }

    private void reloadSchematics(String mode) {
        try {
            // 1. НАДЕЖНАЯ ОЧИСТКА ПАМЯТИ
            // Удаляем старые схемы мода из главного списка игры
            Seq<Schematic> allSchems = Vars.schematics.all();
            if (allSchems != null) {
                for (int i = allSchems.size - 1; i >= 0; i--) {
                    Schematic s = allSchems.get(i);
                    if (s.tags != null && "true".equals(s.tags.get("kernelit-mod-managed"))) {
                        allSchems.remove(i);
                    }
                }
            }

            // Очищаем кэш стартовых схем (схем запуска ядра)
            ObjectMap<CoreBlock, Seq<Schematic>> loadouts = Vars.schematics.getLoadouts();
            if (loadouts != null) {
                loadouts.clear();
            }

            // Перезагружаем базовые схемы игрока с диска (это также восстановит легальные loadouts игрока)
            Vars.schematics.load();
        } catch (Exception e) {
            Log.err("[AdvancedSchemePack] Ошибка при сбросе схем: " + e.getMessage());
        }

        LoadedMod mod = Vars.mods.getMod(this.getClass());
        if (mod == null) return;

        Fi dir = mod.root.child("schems").child(mode);
        if (!dir.exists() || !dir.isDirectory()) return;

        Seq<Schematic> tempSchems = new Seq<>();

        for (Fi file : dir.list()) {
            if (file.extEquals("msav") || file.extEquals("msch")) {
                try {
                    Schematic schem = Schematics.read(file);
                    if (schem.tags == null) schem.tags = new StringMap();
                    
                    // Помечаем схему маркером мода
                    schem.tags.put("kernelit-mod-managed", "true");
                    
                    if(!schem.tags.containsKey("name") || schem.tags.get("name").isEmpty()){
                        schem.tags.put("name", file.nameWithoutExtension());
                    }
                    
                    tempSchems.add(schem);
                } catch (Exception e) {
                    Log.err("[AdvancedSchemePack] Ошибка чтения: " + file.name());
                }
            }
        }

        // Натуральная сортировка (1, 9.1, =10)
        tempSchems.sort((s1, s2) -> {
            String str1 = s1.name().toLowerCase().replace("=", "").replaceAll("\\s+", " ").trim();
            String str2 = s2.name().toLowerCase().replace("=", "").replaceAll("\\s+", " ").trim();

            int i1 = 0, i2 = 0;
            while (i1 < str1.length() && i2 < str2.length()) {
                char c1 = str1.charAt(i1);
                char c2 = str2.charAt(i2);

                if (Character.isDigit(c1) && Character.isDigit(c2)) {
                    StringBuilder num1 = new StringBuilder();
                    StringBuilder num2 = new StringBuilder();

                    while (i1 < str1.length() && (Character.isDigit(str1.charAt(i1)) || str1.charAt(i1) == '.')) {
                        num1.append(str1.charAt(i1));
                        i1++;
                    }
                    while (i2 < str2.length() && (Character.isDigit(str2.charAt(i2)) || str2.charAt(i2) == '.')) {
                        num2.append(str2.charAt(i2));
                        i2++;
                    }

                    try {
                        double d1 = Double.parseDouble(num1.toString());
                        double d2 = Double.parseDouble(num2.toString());
                        int comp = Double.compare(d1, d2);
                        if (comp != 0) return comp;
                    } catch (NumberFormatException e) {
                        int comp = num1.toString().compareTo(num2.toString());
                        if (comp != 0) return comp;
                    }
                } else {
                    if (c1 != c2) return Character.compare(c1, c2);
                    i1++;
                    i2++;
                }
            }
            return Integer.compare(str1.length(), str2.length());
        });

        // 2. БЕЗОПАСНОЕ ДОБАВЛЕНИЕ В ПАМЯТЬ БЕЗ ЗАПИСИ НА ДИСК
        for (Schematic schem : tempSchems) {
            // Добавляем в общий список отображения
            Vars.schematics.all().add(schem);
            
            // Если схема содержит блок ядра, вручную регистрируем её в кэш запуска секторов
            if (schem.hasCore() && Vars.schematics.getLoadouts() != null) {
                // Ищем плитку (tile) с ядром внутри схемы
                mindustry.game.Schematic.Stile coreTile = schem.tiles.find(t -> t.block instanceof CoreBlock);
                if (coreTile != null) {
                    CoreBlock block = (CoreBlock) coreTile.block;
                    if (!Vars.schematics.getLoadouts().containsKey(block)) {
                        Vars.schematics.getLoadouts().put(block, new Seq<>());
                    }
                    Vars.schematics.getLoadouts().get(block).add(schem);
                }
            }
        }
        
        Log.info("[AdvancedSchemePack] Режим [" + mode + "] успешно внедрен в память. Загружено: " + tempSchems.size);
    }

    private void refreshSchematicsDialog() {
        try {
            Table pane = Vars.ui.schematics.cont;
            if(pane != null) pane.clearChildren();
            
            try {
                Method method = Vars.ui.schematics.getClass().getDeclaredMethod("setup");
                method.setAccessible(true);
                method.invoke(Vars.ui.schematics);
            } catch(Exception e) {
                Method method = Vars.ui.schematics.getClass().getDeclaredMethod("build");
                method.setAccessible(true);
                method.invoke(Vars.ui.schematics);
            }
        } catch (Exception ignored) {}
    }

    private String getModeName() {
        String mode = Core.settings.getString("kernelit-schem-mode", "campaign");
        return mode.equals("campaign") ? "Кампания" : "PVP";
    }
}
