package company.hnlz.sendmessage.service;

import java.util.List;


/**
 * 
 * author haoxl
 * date 2019-07-15
 * Description 发送短信工具接口
 */

public interface ISendMessageUtil {
	
	// 注意:此方法只在第一次调用send方法前调用一次!!!
	boolean init (String serialPortName, String sleepTime);
	
	void send(List<String> phoneNumberList, List<String> messageContentList, ICallBack callBack);
	
}
