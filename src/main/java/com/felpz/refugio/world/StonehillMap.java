package com.felpz.refugio.world;

import com.felpz.refugio.Refugio;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Installs the pre-generated Stonehill save chunks into the mod dimension before levels are loaded. */
public final class StonehillMap {
    private static final String RESOURCE_ROOT = "assets/refugio/stonehill_world";
    private static final String MARKER = ".stonehill-map-v1-installed";
    private static final List<String> REGION_FILES = List.of(
            "r.-2.-1.mca", "r.-2.0.mca", "r.-2.1.mca",
            "r.-1.-1.mca", "r.-1.0.mca", "r.-1.1.mca",
            "r.0.-1.mca", "r.0.0.mca", "r.0.1.mca"
    );
    private static final List<String> DATA_TYPES = List.of("region", "entities", "poi");
    private static volatile boolean installed;

    private StonehillMap() { }

    public static boolean isInstalled() {
        return installed;
    }

    public static void install(MinecraftServer server) {
        installed = false;
        Path dimensionRoot = server.getWorldPath(LevelResource.ROOT)
                .resolve("dimensions").resolve("refugio").resolve("stonehill");
        Path marker = dimensionRoot.resolve(MARKER);
        try {
            boolean markerPresent = Files.isRegularFile(marker);
            if (markerPresent && isComplete(dimensionRoot)) {
                installed = true;
                Refugio.LOGGER.info("Chunks Stonehill já instalados em {}.", dimensionRoot);
                return;
            }

            for (String dataType : DATA_TYPES) {
                Path targetDirectory = dimensionRoot.resolve(dataType);
                Files.createDirectories(targetDirectory);
                for (String fileName : REGION_FILES) {
                    String resourceName = RESOURCE_ROOT + "/" + dataType + "/" + fileName;
                    Path target = targetDirectory.resolve(fileName);
                    if (markerPresent && Files.isRegularFile(target) && Files.size(target) >= 8192) continue;
                    try (InputStream input = StonehillMap.class.getClassLoader().getResourceAsStream(resourceName)) {
                        if (input == null) {
                            throw new IOException("Recurso ausente no JAR: " + resourceName);
                        }
                        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            Files.writeString(marker, "Stonehill map data v1\n", StandardCharsets.UTF_8);
            installed = true;
            Refugio.LOGGER.info("Mapa Stonehill instalado em {} (chunks, entidades e POI).", dimensionRoot);
        } catch (IOException | SecurityException exception) {
            Refugio.LOGGER.error("Não foi possível instalar os dados do mapa Stonehill.", exception);
        }
    }

    private static boolean isComplete(Path dimensionRoot) {
        for (String dataType : DATA_TYPES) {
            for (String fileName : REGION_FILES) {
                Path file = dimensionRoot.resolve(dataType).resolve(fileName);
                try {
                    if (!Files.isRegularFile(file) || Files.size(file) < 8192) return false;
                } catch (IOException exception) {
                    return false;
                }
            }
        }
        return true;
    }
}
