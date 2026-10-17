package kernelit;

import arc.Core;
import arc.struct.Seq;
import arc.files.Fi;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.Schematic;
import mindustry.game.Schematics;
import mindustry.mod.Mod;
import mindustry.mod.Mods.LoadedMod;

public class AdvancedSchemePackMod extends Mod {
    private final Seq<Schematic> loadedSchematics = new Seq<>();

    @Override
    public void init() {
        // Загружаем схемы при запуске игры
        String currentMode = Core.settings.getString("kernelit-schem-mode", "campaign");
        reloadSchematics(currentMode);

        // Добавляем вкладку настроек
        Vars.ui.settings.addCategory("Advanced Scheme Pack", (String) null, table -> {
            table.button("", () -> {
                String nextMode = Core.settings.getString("kernelit-schem-mode", "campaign").equals("campaign") ? "pvp" : "campaign";
                Core.settings.put("kernelit-schem-mode", nextMode);
                
                reloadSchematics(nextMode);
                
                if (Vars.ui.schematics.isShown()) {
                    Vars.ui.schematics.hide();
                    Vars.ui.schematics.show();
                }
            }).width(240f).update(b -> {
                b.setText("Режим схем: " + getModeName());
            }).row();
        });
    }

    /** Метод для очистки старых и загрузки новых схем */
    private void reloadSchematics(String mode) {
        // 1. Очищаем старые схемы
        for (Schematic schem : loadedSchematics) {
            Vars.schematics.all().remove(schem);
        }
        loadedSchematics.clear();

        // 2. Получаем ссылку на наш загруженный мод через API игры
        LoadedMod mod = Vars.mods.getMod(this.getClass());
        if (mod == null) {
            Log.err("[AdvancedSchemePack] Не удалось найти структуру мода в памяти игры!");
            return;
        }

        // В Mindustry mod.root указывает на корень мода (будь то папка или JAR-архив)
        // Внутри JAR-файла папка assets является корневой для ресурсов, поэтому child("schems") 
        // сразу ищет папку schems, которую вы положили в assets при разработке.
        Fi dir = mod.root.child("schems").child(mode);
        
        Log.info("[AdvancedSchemePack] Чтение схем из корня мода: " + dir.path());

        if (!dir.exists() || !dir.isDirectory()) {
            Log.err("[AdvancedSchemePack] Папка со схемами не найдена внутри архива мода! Проверьте путь assets/schems/" + mode);
            return;
        }

        // 3. Читаем файлы
        int count = 0;
        for (Fi file : dir.list()) {
            if (file.extEquals("msav") || file.extEquals("msch")) {
                try {
                    Schematic schem = Schematics.read(file);
                    
                    Vars.schematics.all().add(schem);
                    loadedSchematics.add(schem);
                    count++;
                } catch (Exception e) {
                    Log.err("[AdvancedSchemePack] Ошибка чтения файла схемы: " + file.name());
                    Log.err(e);
                }
            }
        }
        
        Log.info("[AdvancedSchemePack] Успешно загружено схем в режиме [" + mode + "]: " + count);
    }

    private String getModeName() {
        String mode = Core.settings.getString("kernelit-schem-mode", "campaign");
        return mode.equals("campaign") ? "Кампания" : "PVP";
    }
}
