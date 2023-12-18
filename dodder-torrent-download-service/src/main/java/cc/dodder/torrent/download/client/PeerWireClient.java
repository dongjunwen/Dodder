package cc.dodder.torrent.download.client;

import cc.dodder.common.entity.Node;
import cc.dodder.common.entity.Torrent;
import cc.dodder.common.entity.Tree;
import cc.dodder.common.util.*;
import cc.dodder.common.util.bencode.BencodingUtils;
import cc.dodder.common.util.snowflake.IdUtils;
import cc.dodder.torrent.download.TorrentDownloadServiceApplication;
import cc.dodder.torrent.download.util.SpringContextUtil;
import ch.qos.logback.core.encoder.ByteArrayUtil;
import ch.qos.logback.core.rolling.helper.FileStoreUtil;
import ch.qos.logback.core.util.FileUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;
import java.util.function.Consumer;

/***
 * Peer Wire 协议客户端，参见协议：
 * http://www.bittorrent.org/beps/bep_0009.html
 *
 * @author: Mr.Xu
 * @create: 2019-02-19 09:21
 **/
public class PeerWireClient {

	private Map<String, Object> map = new HashMap<>();

	private Socket socket;
	private InputStream in;
	private OutputStream out;
	private int protocolLen;
	private int ut_metadata;        //extended message ID
	private int metadata_size;
	private int pieces;
	private int piece = 0;
	private String hexHash;

	private byte[] readBuff = new byte[256];

	private int nextSize;
	private NextFunction next;

	private Torrent torrent;

	private ByteBuf cachedBuff = Unpooled.buffer(22 * 1024);

	private  int MAX_LEN = 32 * 16 * 1024;
	private byte[] metadata;

	private byte[] peerId;
	private byte[] infoHash;
	private String crc64;


	/**
	 * 下载完成监听器
	 */
	private Consumer<Torrent> onFinishedListener;

	public void setOnFinishedListener(Consumer<Torrent> listener) {
		this.onFinishedListener = listener;
	}


	public void downloadMetadata(InetSocketAddress address, byte[] infoHash, String crc64) {
		this.peerId = Constants.PEER_ID;
		this.infoHash = infoHash;
		this.crc64 = crc64;
		try {
			socket = new Socket();
			socket.setSoTimeout(Constants.READ_WRITE_TIMEOUT);
			socket.setReuseAddress(true);
			socket.setTcpNoDelay(true);
			socket.connect(address, Constants.CONNECT_TIMEOUT);

			in = socket.getInputStream();
			out = socket.getOutputStream();

			setNext(1, onProtocolLen);
			sendHandShake(infoHash);

			int len = -1;
			while (!socket.isClosed() && (len = in.read(readBuff)) != -1) {
				cachedBuff.writeBytes(readBuff, 0, len);
				handleMessage();
			}
		} catch (Exception e) {
			//e.printStackTrace();
		} finally {
			/*try {
				if (onFinishedListener != null && torrent != null) {
					onFinishedListener.accept(torrent);
				}
			} catch (Exception ignored) {}*/
			destroy();
		}
	}

	private void sendHandShake(byte[] infoHash) throws Exception {

		/*proctocol*/
		out.write(Constants.BT_PROTOCOL.length() & 0xff);
		out.write(Constants.BT_PROTOCOL.getBytes());

		/*reserved bytes*/
		out.write(Constants.BT_RESERVED);

		/*info_hash*/
		out.write(infoHash);

		/*peer_id*/
		out.write(peerId);

		out.flush();

	}

	private void handleMessage() throws Exception {
		while (cachedBuff.readableBytes() >= nextSize) {
			byte[] buff = new byte[nextSize];
			cachedBuff.readBytes(buff);
			cachedBuff.discardReadBytes();
			next.doNext(buff);
		}
	}

	private NextFunction onMessage = new NextFunction() {
		@Override
		public void doNext(byte[] buff) throws Exception {
			setNext(4, onMessageLength);
			if (buff[0] == Constants.BT_MSG_ID) {
				resolveExtendMessage(buff[1], Arrays.copyOfRange(buff, 2, buff.length));
			}
		}
	};

	private void resolveExtendMessage(byte b, byte[] buf) throws Exception {
		if (b == 0)
			resolveExtendHandShake(BencodingUtils.decode(buf));
		else
			resolvePiece(buf);
	}

	/**
	 * 在str1中从start位置开始查找str2到end位置结束, 返回str2在str1的起始位置, -1表示查找失败
	 */
	public static int strstr(byte[] str1, byte[] str2, int start, int end)
	{
		int index1 = start;
		int index2 = 0;
		if(str2!=null)
		{
			while(index1<str1.length && index1<end)
			{
				int dsite = 0;
				while(str1[index1+dsite]==str2[index2+dsite]) {
					if(index2+dsite+1>=str2.length)
						return index1;
					dsite++;
					if(index1+dsite>=str1.length || index2+dsite>=str2.length)
						break;
				}
				index1++;
			}
			return -1;
		}
		else
			return index1;
	}

	private void resolvePiece(byte[] buff) throws Exception {
		int pos = 1;
		for (; pos < buff.length; pos++) {
			if (buff[pos - 1] == 'e' && buff[pos] == 'e') {
				break;
			}
		}
		if (++pos > buff.length - 1) return;
		byte[] piece_metadata = Arrays.copyOfRange(buff, pos, buff.length);
		if (piece == 0 && piece_metadata[0] != 'd') {	// drop confused packet
			destroy();
			return;
		}
		if ((piece + 1) * 16 * 1024 > MAX_LEN) {
			destroy();
			return;
		}

		int piecesPos = strstr(piece_metadata, "6:pieces".getBytes(), 0, piece_metadata.length);
		if (piecesPos > 0) {    //useless data
			System.arraycopy(piece_metadata, 0, this.metadata, piece * 16 * 1024, piecesPos);
			metadata[piece * 16 * 1024 + piecesPos] = 'e';
			metadata[piece * 16 * 1024 + piecesPos + 1] = 'e';
			metadata[piece * 16 * 1024 + piecesPos + 2] = 'e';
			pieces = -1;
			piece = 0;
		} else {
			System.arraycopy(piece_metadata, 0, this.metadata, piece * 16 * 1024, piece_metadata.length);
		}

		pieces--;
		piece++;
		checkFinished();
		if (pieces > 0)
			requestPiece(piece);
	}

	private void checkFinished() throws Exception {
		if (pieces <= 0) {
			Map map = BencodingUtils.decode(metadata);

			metadata = null;
			if (map != null) {

				torrent = parseTorrent(map);
				if (torrent != null) {
					//System.out.println(JSONUtil.toJSONString(torrent));
					//System.out.println(socket.getInetAddress().getHostAddress() + ":" + socket.getPort() + "   " + hexHash);

					StringRedisTemplate redisTemplate = (StringRedisTemplate) SpringContextUtil.getBean(StringRedisTemplate.class);
					if (!redisTemplate.hasKey(crc64)) {
						//丢进 kafka 消息队列进行入库及索引操作
						//StreamBridge streamBridge = (StreamBridge) SpringContextUtil.getBean(StreamBridge.class);
						//streamBridge.send("download-out-0", JSONUtil.toJSONString(torrent).getBytes());

						//写入本地文件
						String fileName=IdUtils.genFileNo()+".torrent";
						FileUtils.saveFile(JSONUtil.toJSONString(torrent),"/data/s8/torrent/"+fileName);
						redisTemplate.opsForValue().set(crc64, "");
					}
					torrent = null;
				}
			}
			destroy();
		}
	}

	/**
	 * 解析 Torrent 文件信息，封装成对象
	 *
	 * 多文件Torrent的结构的树形图为：
	 *
	 * Multi-file Torrent
	 * ├─announce
	 * ├─announce-list
	 * ├─comment
	 * ├─comment.utf-8
	 * ├─creation date
	 * ├─encoding
	 * ├─info
	 * │ ├─files
	 * │ │ ├─length
	 * │ │ ├─path
	 * │ │ └─path.utf-8
	 * │ ├─name
	 * │ ├─name.utf-8
	 * │ ├─piece length
	 * │ ├─pieces
	 * │ ├─publisher
	 * │ ├─publisher-url
	 * │ ├─publisher-url.utf-8
	 * │ └─publisher.utf-8
	 * └─nodes
	 *
	 * 单文件Torrent的结构的树形图为：
	 *
	 * Single-File Torrent
	 * ├─announce
	 * ├─announce-list
	 * ├─comment
	 * ├─comment.utf-8
	 * ├─creation date
	 * ├─encoding
	 * ├─info
	 * │ ├─length
	 * │ ├─name
	 * │ ├─name.utf-8
	 * │ ├─piece length
	 * │ ├─pieces
	 * │ ├─publisher
	 * │ ├─publisher-url
	 * │ ├─publisher-url.utf-8
	 * │ └─publisher.utf-8
	 * └─nodes
	 *
	 * @param map
	 * @return java.util.Optional<cc.dodder.common.entity.Torrent>
	 */
	private Torrent parseTorrent(Map map) throws Exception {

		String encoding = "UTF-8";
		Map<String, Object> info;
		if (map.containsKey("info"))
			info = (Map<String, Object>) map.get("info");
		else
			info = map;

		if (!info.containsKey("name"))
			return null;
		if (map.containsKey("encoding"))
			encoding = (String) map.get("encoding");

		Torrent torrent = new Torrent();

		if (map.containsKey("creation date"))
			torrent.setCreateDate(((Long) map.get("creation date")).longValue());
		else
			torrent.setCreateDate(System.currentTimeMillis());
		byte[] temp;
		if (info.containsKey("name.utf-8")) {
			temp = (byte[]) info.get("name.utf-8");
			encoding = "UTF-8";
		}
		else {
			temp = (byte[]) info.get("name");
			if (encoding == null) {
				encoding = "UTF-8";
			}
		}

		String fn = new String(temp, encoding);
		torrent.setFileName(fn);
		if ("ru".equals(LanguageUtil.getLanguage(fn))) {
			torrent.setFileNameRu(fn);
		} else {
			torrent.setFileName(fn);
		}
		if (TorrentDownloadServiceApplication.filterSensitiveWords) {
			if (SensitiveWordsUtil.getInstance().containsAny(fn)) {
				torrent.setIsXxx(1);    //标记敏感资源
			}
		}

		//多文件
		if (info.containsKey("files")) {
			List<Map<String, Object>> list = (List<Map<String, Object>>) info.get("files");
			Set<String> types = new HashSet<>();
			List<Node> nodes = new ArrayList<>();
			long total = 0;
			int i = 0;
			int cur = 1, parent = 0;
			for (Map<String, Object> f : list) {
				long length = ((Long) f.get("length")).longValue();
				total += length;

				Long filesize = null;     //null 表示为文件夹
				boolean uft8 = f.containsKey("path.utf-8");
				List<byte[]> aList = uft8 ? (List<byte[]>) f.get("path.utf-8") : (List<byte[]>) f.get("path");
				int j = 0;
				for (byte[] bytes : aList) {
					String sname = new String(bytes, encoding);
					if (sname.contains("_____padding_file_")) {
						j++;
						continue;
					}
					if (j == aList.size() - 1) {
						filesize = length;
						String type = ExtensionUtil.getExtensionType(sname);
						if (type != null) {
							types.add(type);
						}
					}
					Node node = new Node(cur, j == 0 ? null : parent, sname, filesize, i);
					if (!nodes.contains(node)) {
						nodes.add(node);
						parent = cur;
					} else {
						parent = nodes.get(nodes.indexOf(node)).getNid();
					}
					if (cur++ > 1000) {		//文件过多，直接丢掉，目前发现最多的高达将近3万的文件，直接造成web端Dubbo序列化后无法释放，最终内存泄露
						info.clear();
						return null;
					}
					j++;
				}
				i++;
				aList.clear();
				f.clear();
			}
			list.clear();
			Tree tree = new Tree(null);
			tree.createTree(nodes);
			torrent.setFileSize(total);
			torrent.setFiles(JSONUtil.toJSONString(tree));
			nodes.clear();
			tree = null;
			if (types.size() <= 0)
				types.add("其他");
			String sType = String.join(",", types);
			if (sType != null && !"".equals(sType)) {
				torrent.setFileType(sType);
			}
			types.clear();
			nodes.clear();
		} else {
			torrent.setFileSize(((Long) info.get("length")).longValue());

			String type = ExtensionUtil.getExtensionType(torrent.getFileName());
			if (type != null) {
				torrent.setFileType(type);
			}
		}
		info.clear();
		map.clear();
		torrent.setInfoHash(ByteArrayUtil.toHexString(infoHash));
		return torrent;
	}

	private void resolveExtendHandShake(Map map) throws Exception {

		Map m = (Map<String, Object>) map.get("m");

		if (m == null || !m.containsKey("ut_metadata") || !map.containsKey("metadata_size")) {
			destroy();
			return;
		}
		this.ut_metadata = ((Long) m.get("ut_metadata")).intValue();
		this.metadata_size = ((Long) map.get("metadata_size")).intValue();

		if (this.metadata_size > Constants.MAX_METADATA_SIZE) {
			destroy();
			return;
		}
		requestPieces();
	}

	private void requestPieces() throws Exception {
		int len = Math.min(this.metadata_size, MAX_LEN);
		metadata = new byte[len];

		pieces = (int) Math.ceil(this.metadata_size / (16.0 * 1024));

		requestPiece(0);

	}

	private void requestPiece(int piece) throws Exception {
		map.clear();
		map.put("msg_type", 0);
		map.put("piece", piece);

		byte[] data = BencodingUtils.encode(map);

		sendMessage(this.ut_metadata, data);

	}

	private NextFunction onMessageLength = (byte[] buff) -> {
		int length = ByteUtil.byteArrayToInt(buff);
		if (length > 0)
			setNext(length, onMessage);
	};

	private NextFunction onHandshake = (byte[] buff) -> {
		byte[] handshake = Arrays.copyOfRange(buff, protocolLen, buff.length);
		if (handshake[5] == 0x10) {
			setNext(4, onMessageLength);
			sendExtHandShake();
		}
	};

	private NextFunction onProtocolLen = (byte[] buff) -> {
		protocolLen = (int) buff[0];
		//接下来是协议名称(长度：protocolLen)和BT_RESERVED(长度：8)、info_hash(长度：20)、peer_id(长度：20)
		setNext(protocolLen + 48, onHandshake);
	};

	private void sendExtHandShake() throws Exception {
		sendMessage(Constants.EXT_HANDSHAKE_ID, Constants.EXT_HANDSHAKE_DATA);
	}

	private void sendMessage(int id, byte[] data) throws Exception {

		//length prefix bytes
		byte[] length_prefix = ByteUtil.intToByteArray(data.length + 2);
		for(int i=0; i<4; i++)
			length_prefix[i] = (byte)(length_prefix[i] & 0xff);
		out.write(length_prefix);

		//bittorrent message ID, = 20
		out.write(Constants.BT_MSG_ID);

		//extended message ID. 0 = handshake, >0 = extended message as specified by the handshake.
		out.write((byte)(id & 0xff));

		//data
		out.write(data);
		out.flush();
	}

	private interface NextFunction {
		void doNext(byte[] buff)  throws Exception;
	}

	private void setNext(int nextSize, NextFunction next) {
		this.nextSize = nextSize;
		this.next = next;
	}

	private void destroy() {
		crc64 = null;
		peerId = null;
		infoHash = null;
		torrent = null;
		try {
			if (in != null)
				in.close();
		} catch (Exception e) {
		}
		try {
			if (out != null)
				out.close();
		} catch (Exception e) {
		}
		try {
			socket.close();
			socket = null;
		} catch (Exception e) {
		}
		metadata = null;
		piece = 0;
		if (cachedBuff != null) {
			try {
				cachedBuff.clear();
			} catch (Exception e) {
			}
		}
		if (map != null)
			map.clear();
	}


}
