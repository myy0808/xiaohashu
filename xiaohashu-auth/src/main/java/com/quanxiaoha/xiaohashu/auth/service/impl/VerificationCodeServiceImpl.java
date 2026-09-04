package com.quanxiaoha.xiaohashu.auth.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.quanxiaoha.framework.common.exception.BizException;
import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.auth.constant.RedisKeyConstants;
import com.quanxiaoha.xiaohashu.auth.enums.ResponseCodeEnum;
import com.quanxiaoha.xiaohashu.auth.model.vo.verificationcode.SendVerificationCodeReqVO;
import com.quanxiaoha.xiaohashu.auth.service.VerificationCodeService;
import com.quanxiaoha.xiaohashu.auth.sms.AliyunSmsHelper;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;


@RequiredArgsConstructor
@Slf4j
@Service
public class VerificationCodeServiceImpl implements VerificationCodeService {

    private final RedisTemplate redisTemplate;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    private final AliyunSmsHelper aliyunSmsHelper;


    @Override
    public Response<?> send(SendVerificationCodeReqVO sendVerificationCodeReqVO) {
        //获取手机号
        String phone = getPhone(sendVerificationCodeReqVO);
        //构建redis的key
        String key = RedisKeyConstants.buildVerificationCodeKey(phone);
        //判断key是否存在
        Boolean exists = redisTemplate.hasKey(key);
        //存在，则提示验证码请求太频繁
        if (exists) {
            throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_SEND_FREQUENTLY);
        }
        //不存在，则生成6位数字验证码
        String verificationCode = RandomUtil.randomNumbers(6);
        //todo 调用第三方短信服务，发送手机验证码
        threadPoolTaskExecutor.submit(() -> {
            String signName = "速通互联验证码"; // 签名，个人测试签名无法修改
            String templateCode = "100001"; // 短信模板编码
            // 短信模板参数，code 表示要发送的验证码；min 表示验证码有时间时长，即 3 分钟
            String templateParam = String.format("{\"code\":\"%s\",\"min\":\"3\"}", verificationCode);
            aliyunSmsHelper.sendMessage(signName, templateCode, phone, templateParam);
        });
        //存储该手机号的验证码到redis，过期时间为3分钟
        redisTemplate.opsForValue().set(key, verificationCode, 3, TimeUnit.MINUTES);
        //结束，返回结果
        return  Response.success();
    }

    private static String getPhone(SendVerificationCodeReqVO sendVerificationCodeReqVO) {
        return sendVerificationCodeReqVO.getPhone();
    }
}


