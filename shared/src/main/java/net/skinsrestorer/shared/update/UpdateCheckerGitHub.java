/*
 * SkinsRestorer
 * Copyright (C) 2024  SkinsRestorer Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package net.skinsrestorer.shared.update;

import ch.jalu.injector.Injector;
import lombok.RequiredArgsConstructor;
import net.skinsrestorer.api.exception.DataRequestException;
import net.skinsrestorer.api.semver.SemanticVersion;
import net.skinsrestorer.builddata.BuildData;
import net.skinsrestorer.shared.connections.http.HttpClient;
import net.skinsrestorer.shared.connections.http.HttpResponse;
import net.skinsrestorer.shared.exception.DataRequestExceptionShared;
import net.skinsrestorer.shared.log.SRLogger;
import net.skinsrestorer.shared.plugin.SRPlugin;
import net.skinsrestorer.shared.plugin.SRServerPlugin;
import net.skinsrestorer.shared.update.model.GitHubAssetInfo;
import net.skinsrestorer.shared.update.model.GitHubReleaseInfo;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Optional;

/**
 * Credit goes to <a href="https://github.com/InventivetalentDev/SpigetUpdater">SpigetUpdater</a>
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class UpdateCheckerGitHub {
    private static final URI RELEASES_URL_LATEST = URI.create("https://api.github.com/repos/SkinsRestorer/SkinsRestorer/releases/latest");
    private static final String JAR_ASSET_NAME = "SkinsRestorer.jar";
    private static final String VERIFICATION_ASSET_NAME = "verification-hash.txt";
    private static final String LOG_ROW = "§a----------------------------------------------";
    private final SRLogger logger;
    private final SRPlugin plugin;
    private final Injector injector;
    private final HttpClient httpClient;
    private boolean updateDownloaded;

    private static String getCheckerPluginVersion() {
        // Allow overriding the version for unit tests
        if (SRPlugin.isUnitTest()) {
            return System.getProperty("sr.check.version", BuildData.VERSION);
        } else {
            return BuildData.VERSION;
        }
    }

    public void checkForUpdate(UpdateCause cause, UpdateDownloader downloader) {
        try {
            HttpResponse response = httpClient.execute(RELEASES_URL_LATEST,
                    null,
                    HttpClient.HttpType.JSON,
                    plugin.getUserAgent(),
                    HttpClient.HttpMethod.GET,
                    Collections.emptyMap(),
                    90_000);
            GitHubReleaseInfo releaseInfo = response.getBodyAs(GitHubReleaseInfo.class);

            if (releaseInfo.getAssets() == null || releaseInfo.getAssets().isEmpty()) {
                throw new DataRequestExceptionShared("No release info found");
            }

            Optional<String> jarAssetUrl = releaseInfo.getAssets().stream()
                    .filter(asset -> asset.getName().equals(JAR_ASSET_NAME))
                    .map(GitHubAssetInfo::getBrowserDownloadUrl)
                    .findFirst();

            Optional<String> verificationAssetUrl = releaseInfo.getAssets().stream()
                    .filter(asset -> asset.getName().equals(VERIFICATION_ASSET_NAME))
                    .map(GitHubAssetInfo::getBrowserDownloadUrl)
                    .findFirst();

            if (jarAssetUrl.isEmpty()) {
                throw new DataRequestExceptionShared("No jar asset found in release");
            }

            if (isVersionNewer(getCheckerPluginVersion(), releaseInfo.getTagName())) {
                plugin.setOutdated();

                // An update was already downloaded, we don't need to download it again
                if (updateDownloaded) {
                    return;
                }

                String downloadUrl = jarAssetUrl.get();
                if (downloader != null && downloader.downloadUpdate(downloadUrl, verificationAssetUrl.orElse(null))) {
                    updateDownloaded = true;
                }
            } else {
                if (cause == UpdateCause.SCHEDULED) {
                    return;
                }

            }
        } catch (IOException | DataRequestException e) {
            logger.warning("Failed to get release info from api.github.com. \n If this message is repeated a lot, please see https://skinsrestorer.net/firewall");
            logger.debug(e);
        }
    }


    public boolean isVersionNewer(String currentVersion, String newVersion) {
        return SemanticVersion.fromString(newVersion).isNewerThan(SemanticVersion.fromString(currentVersion));
    }
}
