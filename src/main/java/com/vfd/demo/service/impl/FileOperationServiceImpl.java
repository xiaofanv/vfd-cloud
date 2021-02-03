package com.vfd.demo.service.impl;

import com.vfd.demo.bean.FileInfo;
import com.vfd.demo.controller.UploadFileController;
import com.vfd.demo.mapper.FileOperationMapper;
import com.vfd.demo.service.FileOperationService;
import com.vfd.demo.utils.Sm4Utils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @PackageName: com.vfd.demo.service.impl
 * @ClassName: FileOperationServiceImpl
 * @Description:
 * @author: vfdxvffd
 * @date: 2021/1/21 下午8:49
 */
@Service
public class FileOperationServiceImpl implements FileOperationService {

    @Autowired
    FileOperationMapper fileOperationMapper;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Override
    @Transactional
    public Boolean saveFile(FileInfo fileInfo) {
        List<FileInfo> filesByFid = getFilesByFid(fileInfo.getPid(), fileInfo.getOwner());
        for (FileInfo f:filesByFid) {
            if (f.getName().equals(fileInfo.getName())) {
                return false;
            }
        }
        Boolean saveFile = fileOperationMapper.saveFile(fileInfo);
        if (saveFile) {
            return true;
        } else {
            rabbitTemplate.convertAndSend("log.direct","error",this.getClass()+":文件信息录入数据库发生错误:" + fileInfo);
            return false;
        }
    }

    @Override
    public List<FileInfo> getFilesByFid(Integer fid, Integer owner) {
        return fileOperationMapper.getFilesByFid(fid, owner);
    }

    @Override
    public FileInfo getFileById(Integer id, Integer owner) {
        return fileOperationMapper.getFileById(id, owner);
    }

    @Override
    public Boolean deleteFileById(Integer id,Integer owner) {
        return fileOperationMapper.deleteFileById(id, owner);
    }

    @Async
    @Override
    public void deleteFileOnDiskById(Integer id) {
        File file = new File(UploadFileController.PROJECT_DIR + id);
        if (file.exists()) {
            deleteDir(file);
            /*boolean delete1 = Objects.requireNonNull(file.listFiles())[0].delete();
            boolean delete = file.delete();
            if (!(delete1 && delete)) {
                rabbitTemplate.convertAndSend("log.direct","error","deleteFileOnDiskById:文件从硬盘删除发生错误:文件id为:" + id);
            }*/
        }
    }

    @Override
    public void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f:files) {
                if (f.isFile() || f.listFiles() == null) {
                    f.delete();
                } else {
                    deleteDir(f);
                }
            }
        }
        dir.delete();
    }

    @Async
    @Override
    public void deleteTmpDir(File dir) {
        deleteDir(dir);
    }

    @Async
    @Override
    public void encryptFile (String fileName, Integer id) {
        String tmp = UploadFileController.PROJECT_DIR + id + "/~tmp";
        String filePath = UploadFileController.PROJECT_DIR + id + "/" + fileName;
        Sm4Utils.encryptFile("86C63180C2806ED1F47B859DE501215B",filePath,tmp);
        File file = new File(filePath);
        boolean delete = file.delete();
        if (delete) {
            File tmpFile = new File(tmp);
            boolean b = tmpFile.renameTo(file);
        }
    }

    @Override
    public String decryptFile(String fileName, Integer id) {
        String uuid = UUID.randomUUID().toString();
        String tmp = UploadFileController.PROJECT_DIR + id + "/" + uuid;
        File dir = new File(tmp);
        if (!dir.exists()) {
            boolean mkdirs = dir.mkdirs();
        }
        String decrypt = UploadFileController.PROJECT_DIR + id + "/" + uuid +"/" + fileName;
        String source = UploadFileController.PROJECT_DIR + id + "/" + fileName;
        Sm4Utils.decryptFile("86C63180C2806ED1F47B859DE501215B",source,decrypt);
        return uuid;
    }


}
