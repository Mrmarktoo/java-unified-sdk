package cn.leancloud.im.v2;

import cn.leancloud.codec.Base64;
import cn.leancloud.im.DatabaseDelegate;
import cn.leancloud.im.InternalConfiguration;
import cn.leancloud.utils.AVUtils;
import cn.leancloud.utils.StringUtil;
import com.alibaba.fastjson.JSON;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AVIMMessageStorage {
  public static final int MESSAGE_INNERTYPE_BIN = 1;
  public static final int MESSAGE_INNERTYPE_PLAIN = 0;
  public static final String DB_NAME_PREFIX = "com.avos.avoscloud.im.v2.";
  public static final String MESSAGE_TABLE = "messages";
  public static final String MESSAGE_INDEX = "message_index";
  public static final int DB_VERSION = 10;
  public static final String COLUMN_MESSAGE_ID = "message_id";
  public static final String COLUMN_TIMESTAMP = "timestamp";
  public static final String COLUMN_CONVERSATION_ID = "conversation_id";
  public static final String COLUMN_FROM_PEER_ID = "from_peer_id";
  public static final String COLUMN_MESSAGE_DELIVEREDAT = "receipt_timestamp";
  public static final String COLUMN_MESSAGE_READAT = "readAt";
  public static final String COLUMN_MESSAGE_UPDATEAT = "updateAt";
  public static final String COLUMN_PAYLOAD = "payload";
  public static final String COLUMN_STATUS = "status";
  public static final String COLUMN_BREAKPOINT = "breakpoint";
  public static final String COLUMN_DEDUPLICATED_TOKEN = "dtoken";
  public static final String COLUMN_MSG_MENTION_ALL = "mentionAll";
  public static final String COLUMN_MSG_MENTION_LIST = "mentionList";
  public static final String COLUMN_MSG_INNERTYPE = "iType";

  public static final String CONVERSATION_TABLE = "conversations";
  public static final String COLUMN_EXPIREAT = "expireAt";
  public static final String COLUMN_ATTRIBUTE = "attr";
  public static final String COLUMN_INSTANCEDATA = "instanceData";
  public static final String COLUMN_UPDATEDAT = "updatedAt";
  public static final String COLUMN_CREATEDAT = "createdAt";
  public static final String COLUMN_CREATOR = "creator";
  public static final String COLUMN_MEMBERS = "members";
  public static final String COLUMN_LM = "lm";
  public static final String COLUMN_LASTMESSAGE = "last_message";
  public static final String COLUMN_TRANSIENT = "isTransient";
  public static final String COLUMN_UNREAD_COUNT = "unread_count";
  public static final String COLUMN_CONV_MENTIONED = "mentioned";
  public static final String COLUMN_CONVERSATION_READAT = "readAt";
  public static final String COLUMN_CONVRESATION_DELIVEREDAT = "deliveredAt";
  public static final String COLUMN_CONV_LASTMESSAGE_INNERTYPE = "last_msg_iType";
  public static final String COLUMN_CONV_TEMP = "temp";
  public static final String COLUMN_CONV_TEMP_TTL = "temp_ttl";
  public static final String COLUMN_CONV_SYSTEM = "sys";

  public static final String NUMBERIC = "NUMBERIC";
  public static final String INTEGER = "INTEGER";
  public static final String BLOB = "BLOB";
  public static final String TEXT = "TEXT";
  public static final String VARCHAR32 = "VARCHAR(32)";

  public static class SQL {
    static final String TIMESTAMP_MORE_OR_TIMESTAMP_EQUAL_BUT_MESSAGE_ID_MORE_AND_CONVERSATION_ID =
            " ( " +
                    COLUMN_TIMESTAMP + " > ? or (" + COLUMN_TIMESTAMP + " = ? and " + COLUMN_MESSAGE_ID
                    + " > ? )) and " +
                    COLUMN_CONVERSATION_ID + " = ? ";

    static final String TIMESTAMP_LESS_AND_CONVERSATION_ID = COLUMN_TIMESTAMP + " < ? and "
            + COLUMN_CONVERSATION_ID + " = ? ";

    // 在时间戳第一排序，MessageId 第二排序的情况下，找到时间戳小于，或者时间戳等于但MessageId小于的消息
    // 三条消息(时间戳/MessageId) 2/a、1/a、1/b， 则用 1/b 来找历史消息的时候，返回1/a
    static final String TIMESTAMP_LESS_OR_TIMESTAMP_EQUAL_BUT_MESSAGE_ID_LESS_AND_CONVERSATION_ID =
            " ( " +
                    COLUMN_TIMESTAMP + " < ? or (" + COLUMN_TIMESTAMP + " = ? and " + COLUMN_MESSAGE_ID
                    + " < ? )) and " +
                    COLUMN_CONVERSATION_ID + " = ? ";

    static final String ORDER_BY_TIMESTAMP_DESC_THEN_MESSAGE_ID_DESC =
            COLUMN_TIMESTAMP + " desc, " + COLUMN_MESSAGE_ID + " desc";

    static final String ORDER_BY_TIMESTAMP_ASC_THEN_MESSAGE_ID_ASC =
            COLUMN_TIMESTAMP + " , " + COLUMN_MESSAGE_ID;

    static final String DELETE_LOCAL_MESSAGE = COLUMN_CONVERSATION_ID + " = ? and " + COLUMN_MESSAGE_ID + " = ? and "
            + COLUMN_STATUS + " = ? and " + COLUMN_DEDUPLICATED_TOKEN + " = ? ";

    static final String SELECT_VALID_CONVS = "("+ COLUMN_CONV_TEMP + " < 1 and " + COLUMN_EXPIREAT + " > ?) or (" + COLUMN_CONV_TEMP + "> 0 and " + COLUMN_CONV_TEMP_TTL + " > ?)";
  }

  public interface StorageQueryCallback {
    void done(List<AVIMMessage> messages, List<Boolean> breakpoints);
  }

  public interface StorageMessageCallback {
    void done(AVIMMessage message, boolean breakpoint);
  }

  private static ConcurrentHashMap<String, AVIMMessageStorage> storages =
          new ConcurrentHashMap<String, AVIMMessageStorage>();
  public static AVIMMessageStorage getInstance(String clientId) {
    AVIMMessageStorage storage = storages.get(clientId);
    if (null == storage) {
      storage = new AVIMMessageStorage(clientId);
      AVIMMessageStorage elderStorage = storages.putIfAbsent(clientId, storage);
      if (null != elderStorage) {
        storage = elderStorage;
      }
    }
    return storage;
  }

  private DatabaseDelegate delegate = null;
  private String clientId = null;

  private AVIMMessageStorage(String clientId) {
    this.clientId = clientId;
    this.delegate = InternalConfiguration.getDatabaseDelegate();
  }

  public void insertMessage(AVIMMessage message, boolean breakpoint) {
    if (null == message) {
      return;
    }
    insertMessages(Arrays.asList(message), breakpoint);
  }

  private synchronized int insertMessages(List<AVIMMessage> messages, boolean breakpoint) {
    int insertCount = 0;

    if (null == this.delegate) {
      return insertCount;
    }
    for (AVIMMessage message: messages) {
      Map<String, Object> values = new HashMap<>();
      values.put(COLUMN_CONVERSATION_ID, message.getConversationId());
      values.put(COLUMN_MESSAGE_ID, message.getMessageId());
      values.put(COLUMN_TIMESTAMP, message.getTimestamp());
      values.put(COLUMN_FROM_PEER_ID, message.getFrom());
      if (message instanceof AVIMBinaryMessage) {
        values.put(COLUMN_PAYLOAD, ((AVIMBinaryMessage)message).getBytes());
        values.put(COLUMN_MSG_INNERTYPE, MESSAGE_INNERTYPE_BIN);
      } else {
        values.put(COLUMN_PAYLOAD, message.getContent().getBytes());
        values.put(COLUMN_MSG_INNERTYPE, MESSAGE_INNERTYPE_PLAIN);
      }
      values.put(COLUMN_MESSAGE_DELIVEREDAT, message.getDeliveredAt());
      values.put(COLUMN_MESSAGE_READAT, message.getReadAt());
      values.put(COLUMN_MESSAGE_UPDATEAT, message.getUpdateAt());
      values.put(COLUMN_STATUS, message.getMessageStatus().getStatusCode());
      values.put(COLUMN_BREAKPOINT, breakpoint ? 1 : 0);

      values.put(COLUMN_MSG_MENTION_ALL, message.isMentionAll()? 1: 0);
      values.put(COLUMN_MSG_MENTION_LIST, message.getMentionListString());
      insertCount += this.delegate.insert(MESSAGE_TABLE, values);
    }
    return insertCount;
  }

  public synchronized boolean insertLocalMessage(AVIMMessage message) {
    if (null == message) {
      return false;
    }
    if (!StringUtil.isEmpty(message.getMessageId()) || StringUtil.isEmpty(message.getConversationId())
            || StringUtil.isEmpty(message.getUniqueToken())) {
      return false;
    }
    if (null == this.delegate) {
      return true;
    }
    String internalMessageId = generateInternalMessageId(message.getUniqueToken());
    Map<String, Object> values = new HashMap<>();
    values.put(COLUMN_CONVERSATION_ID, message.getConversationId());
    values.put(COLUMN_MESSAGE_ID, internalMessageId);
    values.put(COLUMN_TIMESTAMP, message.getTimestamp());
    values.put(COLUMN_FROM_PEER_ID, message.getFrom());
    if (message instanceof AVIMBinaryMessage) {
      values.put(COLUMN_PAYLOAD, ((AVIMBinaryMessage)message).getBytes());
      values.put(COLUMN_MSG_INNERTYPE, MESSAGE_INNERTYPE_BIN);
    } else {
      values.put(COLUMN_PAYLOAD, message.getContent().getBytes());
    }
    values.put(COLUMN_MESSAGE_DELIVEREDAT, message.getDeliveredAt());
    values.put(COLUMN_MESSAGE_READAT, message.getReadAt());
    values.put(COLUMN_MESSAGE_UPDATEAT, message.getUpdateAt());
    values.put(COLUMN_STATUS, AVIMMessage.AVIMMessageStatus.AVIMMessageStatusFailed.getStatusCode());
    values.put(COLUMN_BREAKPOINT, 0);
    values.put(COLUMN_DEDUPLICATED_TOKEN, message.getUniqueToken());

    values.put(COLUMN_MSG_MENTION_ALL, message.isMentionAll()? 1: 0);
    values.put(COLUMN_MSG_MENTION_LIST, message.getMentionListString());
    int ret = this.delegate.insert(MESSAGE_TABLE, values);
    return ret > 0;
  }

  public synchronized boolean removeLocalMessage(AVIMMessage message) {
    if (null == message
            || StringUtil.isEmpty(message.getConversationId())
            || StringUtil.isEmpty(message.getUniqueToken())) {
      return false;
    }
    if (null == this.delegate) {
      return true;
    }
    String internalMessageId = generateInternalMessageId(message.getUniqueToken());
    String status = String.valueOf(AVIMMessage.AVIMMessageStatus.AVIMMessageStatusFailed.getStatusCode());
    int ret = this.delegate.delete(MESSAGE_TABLE, SQL.DELETE_LOCAL_MESSAGE,
            new String[]{message.getConversationId(), internalMessageId, status, message.getUniqueToken()});
    return ret > 0;
  }

  public void insertContinuousMessages(List<AVIMMessage> messages, String conversationId) {
    if (null == this.delegate) {
      return;
    }
    if (null == messages || messages.isEmpty() || StringUtil.isEmpty(conversationId)) {
      return;
    }
    AVIMMessage firstMessage = messages.get(0);
    List<AVIMMessage> tailMessages = messages.subList(1, messages.size());
    AVIMMessage lastMessage = messages.get(messages.size() - 1);
    if (!containMessage(lastMessage)) {
      AVIMMessage eldestMessage = getNextMessage(lastMessage);
      if (eldestMessage != null) {
        updateBreakpoints(Arrays.asList(eldestMessage), true, conversationId);
      }
    }
    if (!tailMessages.isEmpty()) {
      insertMessages(tailMessages, false);
      // remove breakpoints
      updateBreakpoints(tailMessages, false, conversationId);
    }
    insertMessage(firstMessage, true);
  }

  public boolean containMessage(AVIMMessage message) {
    if (null == this.delegate) {
      return false;
    }
    int cnt = this.delegate.queryCount(MESSAGE_TABLE, new String[] {},
            getWhereClause(COLUMN_CONVERSATION_ID, COLUMN_MESSAGE_ID),
            new String[] {message.getConversationId(), message.getMessageId()}, null, null, null);
    return cnt > 0;
  }

  protected synchronized void updateBreakpoints(List<AVIMMessage> messages,
                                                boolean breakpoint, String conversationId) {
    int batchSize = 900;
    if (messages.size() > batchSize) {
      updateBreakpointsForBatch(messages.subList(0, batchSize), breakpoint, conversationId);
      updateBreakpoints(messages.subList(batchSize, messages.size()), breakpoint, conversationId);
    } else {
      updateBreakpointsForBatch(messages, breakpoint, conversationId);
    }
  }

  private synchronized int updateBreakpointsForBatch(List<AVIMMessage> messages,
                                                     boolean breakpoint, String conversationId) {
    if (null == this.delegate) {
      return 0;
    }
    String[] arguments = new String[messages.size()];
    List<String> placeholders = new ArrayList<String>();
    int i;
    for (i = 0; i < messages.size(); i++) {
      AVIMMessage message = messages.get(i);
      arguments[i] = message.getMessageId();
      placeholders.add("?");
    }
    Map<String, Object> cv = new HashMap<>();
    cv.put(COLUMN_BREAKPOINT, breakpoint ? 1 : 0);
    String joinedPlaceholders = StringUtil.join(",", placeholders);
    return this.delegate.update(MESSAGE_TABLE, cv, COLUMN_MESSAGE_ID
            + " in (" + joinedPlaceholders + ") ", arguments);
  }

  public synchronized boolean updateMessage(AVIMMessage message, String originalId) {
    if (null == this.delegate) {
      return false;
    }
    Map<String, Object> values = new HashMap<>();
    values.put(COLUMN_TIMESTAMP, message.getTimestamp());
    values.put(COLUMN_STATUS, message.getMessageStatus().getStatusCode());
    values.put(COLUMN_MESSAGE_DELIVEREDAT, message.getDeliveredAt());
    values.put(COLUMN_MESSAGE_READAT, message.getReadAt());
    values.put(COLUMN_MESSAGE_UPDATEAT, message.getUpdateAt());
    values.put(COLUMN_MESSAGE_ID, message.getMessageId());

    values.put(COLUMN_MSG_MENTION_ALL, message.isMentionAll()? 1: 0);
    values.put(COLUMN_MSG_MENTION_LIST, message.getMentionListString());
    int ret = this.delegate.update(MESSAGE_TABLE, values, getWhereClause(COLUMN_MESSAGE_ID), new String[] {originalId});
    return ret > -1;
  }

  synchronized boolean updateMessageForPatch(AVIMMessage message) {
    if (null == this.delegate) {
      return false;
    }
    Map<String, Object> values = new HashMap<>();
    if (message instanceof AVIMBinaryMessage) {
      values.put(COLUMN_PAYLOAD, ((AVIMBinaryMessage)message).getBytes());
      values.put(COLUMN_MSG_INNERTYPE, MESSAGE_INNERTYPE_BIN);
    } else {
      values.put(COLUMN_PAYLOAD, message.getContent());
      values.put(COLUMN_MSG_INNERTYPE, MESSAGE_INNERTYPE_PLAIN);
    }
    values.put(COLUMN_STATUS, message.getMessageStatus().getStatusCode());
    values.put(COLUMN_MESSAGE_UPDATEAT, message.getUpdateAt());
    int ret = this.delegate.update(MESSAGE_TABLE, values, getWhereClause(COLUMN_MESSAGE_ID),
            new String[] {message.getMessageId()});
    return ret > -1;
  }

  public synchronized void deleteMessages(List<AVIMMessage> messages, String conversationId) {
    if (null == this.delegate) {
      return;
    }
    for (AVIMMessage message : messages) {
      String messageId = message.getMessageId();
      AVIMMessage nextMessage = getNextMessage(message);
      if (nextMessage != null) {
        updateBreakpoints(Arrays.asList(message), true, conversationId);
      }
      this.delegate.delete(MESSAGE_TABLE, getWhereClause(COLUMN_MESSAGE_ID), new String[] {messageId});
    }
  }

  public synchronized void deleteConversationData(String conversationId) {
    if (null == this.delegate) {
      return;
    }
    this.delegate.delete(MESSAGE_TABLE, getWhereClause(COLUMN_CONVERSATION_ID),
            new String[] {conversationId});
    this.delegate.delete(CONVERSATION_TABLE, getWhereClause(COLUMN_CONVERSATION_ID),
            new String[] {conversationId});
  }

  public synchronized void deleteClientData() {
    if (null == this.delegate) {
      return;
    }
    this.delegate.delete(MESSAGE_TABLE, null, null);
    this.delegate.delete(CONVERSATION_TABLE, null, null);
  }

  void getMessage(String msgId, long timestamp, String conversationId,
                  StorageMessageCallback callback) {
    if (timestamp <= 0) {
      callback.done(null, false);
    } else if (null == this.delegate){
      callback.done(null, false);
    } else {
      List<AVIMMessage> result = null;
      if (msgId == null) {
        result = this.delegate.queryMessages(null, getWhereClause(COLUMN_TIMESTAMP, COLUMN_CONVERSATION_ID),
                new String[] {Long.toString(timestamp), conversationId}, null, null, null, "1");
      } else {
        result = this.delegate.queryMessages(null, getWhereClause(COLUMN_MESSAGE_ID),
                new String[] {msgId}, null, null, null, "1");
      }
      // TODO: fix me!
      callback.done(result.get(0), false);
    }
  }

  public void getMessages(String msgId, long timestamp, int limit, String conversationId,
                          StorageQueryCallback callback) {
    if (null == this.delegate) {
      callback.done(null, null);
    }
    String selection;
    String[] selectionArgs;
    if (timestamp > 0) {
      if (msgId == null) {
        selection = SQL.TIMESTAMP_LESS_AND_CONVERSATION_ID;
        selectionArgs = new String[] {Long.toString(timestamp), conversationId};
      } else {
        selection = SQL.TIMESTAMP_LESS_OR_TIMESTAMP_EQUAL_BUT_MESSAGE_ID_LESS_AND_CONVERSATION_ID;
        selectionArgs =
                new String[] {Long.toString(timestamp), Long.toString(timestamp), msgId, conversationId};
      }
    } else {
      selection = getWhereClause(COLUMN_CONVERSATION_ID);
      selectionArgs = new String[] {conversationId};
    }
    List<AVIMMessage> results = this.delegate.queryMessages(null, selection, selectionArgs, null, null,
            SQL.ORDER_BY_TIMESTAMP_DESC_THEN_MESSAGE_ID_DESC, limit + "");
    // TODO: fix me!
    callback.done(results, null);
  }

  public long getMessageCount(String conversationId) {
    if (null == this.delegate) {
      return 0l;
    }
    AVIMMessage lastBreakPointMessage = getLatestMessageWithBreakpoint(conversationId, true);
    long messageCount = 0l;
    String query;
    String[] queryArgs;
    if (null == lastBreakPointMessage) {
      query = "select count(*) from " + MESSAGE_TABLE + " where " + COLUMN_CONVERSATION_ID + " = ?";
      queryArgs = new String[] {conversationId};
    } else {
      query = "select count(*) from " + MESSAGE_TABLE + " where "
              + COLUMN_CONVERSATION_ID + " = ? and (" + COLUMN_TIMESTAMP + " > ? or ( "
              + COLUMN_TIMESTAMP + " = ? and "
              + COLUMN_MESSAGE_ID + " >= ? )) order by "
              + SQL.ORDER_BY_TIMESTAMP_DESC_THEN_MESSAGE_ID_DESC;
      queryArgs = new String[] {conversationId, String.valueOf(lastBreakPointMessage.getTimestamp()),
                      String.valueOf(lastBreakPointMessage.getTimestamp()),
                      lastBreakPointMessage.getMessageId()};
    }
    return this.delegate.queryCount(MESSAGE_TABLE, null, query, queryArgs, null, null, null);
  }

  protected AVIMMessage getNextMessage(AVIMMessage currentMessage) {
    if (null == this.delegate) {
      return null;
    }
    List<AVIMMessage> result = this.delegate.queryMessages(null,
            SQL.TIMESTAMP_MORE_OR_TIMESTAMP_EQUAL_BUT_MESSAGE_ID_MORE_AND_CONVERSATION_ID,
            new String[] {Long.toString(currentMessage.getTimestamp()),
                    Long.toString(currentMessage.getTimestamp()),
                    currentMessage.getMessageId(), currentMessage.getConversationId()}, null, null,
            SQL.ORDER_BY_TIMESTAMP_ASC_THEN_MESSAGE_ID_ASC, "1");
    if (null == result || result.size() < 1) {
      return null;
    }
    return result.get(0);
  }

  AVIMMessage getLatestMessage(String conversationId) {
    if (null == this.delegate) {
      return null;
    }
    List<AVIMMessage> result = this.delegate.queryMessages(null, getWhereClause(COLUMN_CONVERSATION_ID),
            new String[] {conversationId}, null, null,
            SQL.ORDER_BY_TIMESTAMP_DESC_THEN_MESSAGE_ID_DESC, "1");
    if (null == result || result.size() < 1) {
      return null;
    }
    return result.get(0);
  }

  AVIMMessage getLatestMessageWithBreakpoint(String conversationId, boolean breakpoint) {
    if (null == this.delegate) {
      return null;
    }
    List<AVIMMessage> result = this.delegate.queryMessages(null,
            getWhereClause(COLUMN_CONVERSATION_ID, COLUMN_BREAKPOINT),
            new String[] {conversationId, breakpoint ? "1" : "0"}, null, null,
            SQL.ORDER_BY_TIMESTAMP_DESC_THEN_MESSAGE_ID_DESC, "1");
    if (null == result || result.size() < 1) {
      return null;
    }
    return result.get(0);
  }

  private String generateInternalMessageId(String uniqueToken) {
    if (StringUtil.isEmpty(uniqueToken)) {
      return "";
    }
    return uniqueToken;
  }

  private static String getWhereClause(String... columns) {
    List<String> conditions = new ArrayList<String>();
    for (String column : columns) {
      conditions.add(column + " = ? ");
    }
    return StringUtil.join(" and ", conditions);
  }

  public int insertConversations(List<AVIMConversation> conversations) {
    if (null == this.delegate) {
      return 0;
    }
    for (AVIMConversation conversation : conversations) {
      Map<String, Object> values = new HashMap<>();
      values.put(COLUMN_ATTRIBUTE, JSON.toJSONString(conversation.attributes));
      values.put(COLUMN_INSTANCEDATA, JSON.toJSONString(conversation.instanceData));
      values.put(COLUMN_CREATEDAT, conversation.createdAt);
      values.put(COLUMN_UPDATEDAT, conversation.updatedAt);
      values.put(COLUMN_CREATOR, conversation.creator);
      values.put(COLUMN_EXPIREAT, System.currentTimeMillis()
              + Conversation.DEFAULT_CONVERSATION_EXPIRE_TIME_IN_MILLS);
      if (conversation.lastMessageAt != null) {
        values.put(COLUMN_LM, conversation.lastMessageAt.getTime());
      }

      final AVIMMessage message = conversation.getLastMessage();
      if (null != message) {
        if (message instanceof AVIMBinaryMessage) {
          byte[] bytes = ((AVIMBinaryMessage)message).getBytes();
          String base64Msg = Base64.encodeToString(bytes, Base64.NO_WRAP);
          values.put(COLUMN_LASTMESSAGE, base64Msg);
          values.put(COLUMN_CONV_LASTMESSAGE_INNERTYPE, MESSAGE_INNERTYPE_BIN);
        } else {
          String lastMessage = JSON.toJSONString(message);
          values.put(COLUMN_LASTMESSAGE, lastMessage);
          values.put(COLUMN_CONV_LASTMESSAGE_INNERTYPE, MESSAGE_INNERTYPE_PLAIN);
        }
      }

      values.put(COLUMN_MEMBERS, JSON.toJSONString(conversation.getMembers()));
      values.put(COLUMN_TRANSIENT, conversation.isTransient ? 1 : 0);
      values.put(COLUMN_UNREAD_COUNT, conversation.getUnreadMessagesCount());

      values.put(COLUMN_CONV_MENTIONED, conversation.unreadMessagesMentioned()? 1:0);

      values.put(COLUMN_CONVERSATION_READAT, conversation.getLastReadAt());
      values.put(COLUMN_CONVRESATION_DELIVEREDAT, conversation.getLastDeliveredAt());
      values.put(COLUMN_CONVERSATION_ID, conversation.getConversationId());

      // add temporary conversation data.
      values.put(COLUMN_CONV_SYSTEM, conversation.isSystem()? 1 : 0);
      values.put(COLUMN_CONV_TEMP, conversation.isTemporary()? 1 : 0);
      values.put(COLUMN_CONV_TEMP_TTL, conversation.getTemporaryExpiredat());
    }
    return 1;
  }

  public AVIMConversation getConversation(String conversationId) {
    return null;
  }

  boolean updateConversationTimes(AVIMConversation conversation) {
    if (getConversation(conversation.getConversationId()) != null) {
      Map<String, Object> values = new HashMap<>();
      values.put(COLUMN_CONVERSATION_READAT, conversation.getLastReadAt());
      values.put(COLUMN_CONVRESATION_DELIVEREDAT, conversation.getLastDeliveredAt());
      int ret = this.delegate.update(CONVERSATION_TABLE, values, getWhereClause(COLUMN_CONVERSATION_ID),
              new String[] {conversation.getConversationId()});
      return ret != -1;
    }
    return false;
  }

  boolean updateConversationUreadCount(String conversationId, long unreadCount, boolean mentioned) {
    if (getConversation(conversationId) != null) {
      Map<String, Object> values = new HashMap<>();
      values.put(COLUMN_UNREAD_COUNT, unreadCount);
      values.put(COLUMN_CONV_MENTIONED, mentioned? 1:0);
      int ret = this.delegate.update(CONVERSATION_TABLE, values, getWhereClause(COLUMN_CONVERSATION_ID),
              new String[] {conversationId});
      return ret > -1;
    }
    return false;
  }

  public boolean updateConversationLastMessageAt(AVIMConversation conversation) {
    if (getConversation(conversation.getConversationId()) != null
            && conversation.getLastMessageAt() != null) {
      Map<String, Object> values = new HashMap<>();
      values.put(COLUMN_LM, conversation.getLastMessageAt().getTime());
      int ret = this.delegate.update(CONVERSATION_TABLE, values, getWhereClause(COLUMN_CONVERSATION_ID),
              new String[] {conversation.getConversationId()});
      return ret > -1;
    }
    return false;
  }
}
