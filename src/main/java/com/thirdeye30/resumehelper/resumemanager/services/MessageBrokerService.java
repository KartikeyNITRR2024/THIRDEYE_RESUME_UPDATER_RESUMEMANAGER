package com.thirdeye30.resumehelper.resumemanager.services;

import java.util.List;

import com.thirdeye30.resumehelper.resumemanager.dtos.Message;
import com.thirdeye30.resumehelper.resumemanager.dtos.StatusResume;

public interface MessageBrokerService {

	void sendMessages(String topicName, Object messagess);

	List<Message<StatusResume>> getMessage(String topicName);

}

