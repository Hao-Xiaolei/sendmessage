package company.hnlz.sendmessage.service;


/**
 * 
 * author haoxl
 * date 2019-07-15
 * Description 发送短信公共方法中的回调函数接口
 */

public interface ICallBack {
	
	void call(String phoneNumber, String messageContent, Boolean result);

}
