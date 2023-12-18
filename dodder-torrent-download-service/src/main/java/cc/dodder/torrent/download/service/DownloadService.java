package cc.dodder.torrent.download.service;

import cc.dodder.common.entity.DownloadMsgInfo;

/**
 * @author jerry
 * @since 2023/12/18 11:08
 */
public interface DownloadService {
    void downloadTorrent(DownloadMsgInfo downloadMsgInfo);
}
