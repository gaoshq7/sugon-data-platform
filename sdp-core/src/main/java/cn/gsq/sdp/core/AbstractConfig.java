package cn.gsq.sdp.core;

import cn.gsq.common.config.GalaxySpringUtil;
import cn.gsq.sdp.*;
import cn.gsq.sdp.core.annotation.Config;
import cn.gsq.sdp.core.utils.CSVConverter;
import cn.gsq.sdp.driver.ConfigDriver;
import cn.gsq.sdp.utils.FileUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.text.StrBuilder;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Project : sugon-data-platform
 * Class : cn.gsq.sdp.core.AbstractConfig
 *
 * @author : gsq
 * @date : 2025-02-28 11:02
 * @note : It's not technology, it's art !
 **/
@Slf4j
public abstract class AbstractConfig extends AbstractSdpComponent implements Configuration {

    /*
     *  配置文件加载流程：
     *   1、进行加载前预处理，包括创建文件夹等基础操作：preAction(AbstractConfig config)。
     *   2、初始化配置文件，需要子类提供initialization抽象方法：initConfig()。
     *  ⚠️ 注意：tamper方法可在任何时候对配置文件进行属性添加或者覆盖！
     * */

    @Getter
    private transient AbstractServe serve;    // 所在服务

    @Getter
    private final String name;    // 配置文件名称

    @Getter
    private final String path;    //  配置文件需要放置的地址（ ⚠️ 分支里面必有该地址）

    @Getter
    private final String description;     //  配置文件描述信息

    @Getter
    private final String configType;     //  配置文件类型 xml  text cfg..

    @Getter
    private final int order;      // 配置文件列表排序

    private final Map<String, Branch> branches = MapUtil.newHashMap();     // 所有配置文件的分支

    private final Map<String, List<ConfigItem>> dictionaryMap = MapUtil.newHashMap(); // 所有配置文件的字典

    private final Map<String,List<ConfigItem>> nonDictionaryMap = MapUtil.newHashMap(); // 所有没有字典的配置文件

    protected AbstractConfig() {
        Config config = this.getClass().getAnnotation(Config.class);
        AbstractSdpManager manager = GalaxySpringUtil.getBean(AbstractSdpManager.class);
        // 取路径最后一个文件名作为配置文件名称
        String[] pathAndFile = this.getClass().getAnnotation(Config.class).path().split(StrUtil.SLASH);
        this.name = pathAndFile[pathAndFile.length - 1];
        this.configType = config.type();
        this.description = config.description();
        this.order = config.order();
        this.path = manager.getHome() + (config.path().startsWith(StrUtil.SLASH) ? config.path() : StrUtil.SLASH + config.path());
        // 创建配置文件分支
        // 获取配置文件在jar包中的根目录
        StrBuilder builder = StrBuilder.create();
        builder.append(StrUtil.removeAll(manager.getVersion(), StrUtil.DOT))
                .append(StrUtil.SLASH).append(this.getServeNameByClass())
                .append(StrUtil.SLASH);
        // 获取分支名称
        String[] bnames = config.branches();
        // ⚠️ 获取分支配置文件在jar包中的具体地址
        String classPath = builder + FileUtil.getPrefix(getName()) + StrUtil.SLASH + "{}" + StrUtil.DOT + FileUtil.getSuffix(getName());
        // ⚠️ 创建分支实例（没有分支概念则返回空数组）
        List<Branch> branches = Arrays.stream(bnames)
                .map(name -> new Branch(FileUtil.getPrefix(name), StrUtil.format(classPath, name)))
                .collect(Collectors.toList());
        // 没有分支则添加默认分支
        if(CollUtil.isEmpty(branches)) {
            branches.add(new Branch(SdpPropertiesFinal.DEFAULT_CHAR, builder + getName()));
        }
        for (Branch branch : branches) {
            this.branches.put(branch.getName(), branch);
        }
    }

    /**
     * @Description : 在serve调用initProperty()时判断是否属于某个服务
     **/
    protected boolean isBelong(Class<? extends AbstractServe> clazz) {
        Config config = this.getClass().getAnnotation(Config.class);
        return config.master() == clazz;
    }

    /**
     * @Description : 在serve调用initProperty()时获取所属服务名称
     **/
    protected String getServeNameByClass() {
        Config config = this.getClass().getAnnotation(Config.class);
        return config.master().getSimpleName();
    }

    /**
     * @Description : 系统启动时初始化注解中的属性
     * @note : ⚠️ 程序启动配置文件的入口 !
     **/
    @Override
    protected void initProperty() {
        // 有分支：配置文件名称弃掉后缀名/分支名称.后缀名；没有分支：配置文件名称.后缀名
        Config config = this.getClass().getAnnotation(Config.class);
        this.serve = GalaxySpringUtil.getBean(config.master());
    }

    /**
     * @Description : 程序启动初始化配置文件分支
     * @note : ⚠️ 只有在程序启动时运行一次 !
     **/
    @Override
    protected void loadEnvResource() {
        this.branches.forEach((name, branch) -> {
            branch.initContent();   // 初始化分支内容
            this.dictionaryMap.put(name, branch.getConfigDictionary());     // 字典写入内存
            this.nonDictionaryMap.put(name, branch.getNonConfigDictionary());   // 不是字典的也写入内存
        });
    }

    /**
     * @Description : 安装服务时配置文件初始化入口
     * @note : ⚠️ 只在安装时运行一次 !
     **/
    protected void install(Blueprint.Serve blueprintServe) {
        // 在蓝图中找到对应当前配置文件的信息
        Blueprint.Config config = CollUtil.findOne(blueprintServe.getConfigs(),
                bpc -> bpc.getConfigname().equals(this.getName()));
        // 蓝图配置覆盖默认配置（ ⚠️ 仅修改内存中的内容，不进行各个主机同步！ ）
        if(ObjectUtil.isNotEmpty(config)) config.getBranches().forEach(this::tamper);
        Map<String, Map<String, String>> branches = MapUtil.map(this.branches, (k, v) -> v.getBranchStrMap());

        // 获取子类不同分支编辑后的配置文件内容
        List<BranchModel> branchModels = initContents(branches, blueprintServe);
        // 使用配置文件同步引擎进程同步
        for(BranchModel branchModel : branchModels) {
            Branch branch = this.branches.get(branchModel.getName());
            // 添加分支配置文件覆盖节点
            branch.addHosts(ArrayUtil.toArray(branchModel.getHostnames(), String.class));
            // 修改配置文件同步各个节点
            branch.updateContent(branchModel.getContent());
        }
    }

    /**
     * @Description : 卸载配置文件
     **/
    @Override
    protected void recover() {
        for(Branch branch : this.branches.values()) {
            branch.destroy();
        }
    }

    /**
     * @Description : 篡改配置文件
     * @note : ⚠️ 没有的键值添加，有的则覆盖，不同步，删除配置无效，仅在蓝图装配的时候运行一次 !
     **/
    protected void tamper(String branchName, Map<String, String> items) {
        Branch branch = this.branches.get(branchName);
        if(ObjectUtil.isNotNull(branch)) {
            items.forEach((k, v) -> {
                ConfigItem item = CollUtil.findOne(branch.getItems(), it -> it.getKey().equals(k));
                if(ObjectUtil.isNotEmpty(item)) {
                    item.setValue(v);   // 初始配置中存在
                } else {
                    if(CollUtil.isNotEmpty(branch.getConfigDictionary())){
                        List<ConfigItem> collect =branch.getConfigDictionary().stream().filter(s -> s.getKey().equals(k)).collect(Collectors.toList());
                        if(ObjectUtil.isNotEmpty(collect)){
                            item=collect.get(0) ;
                        }
                        item = ObjectUtil.isEmpty(item) ? new ConfigItem().setKey(k).setValue(v).setIsInDictionary(false) : item;
                        branch.items.add(item);
                    }
                }
            });
        } else {
            log.warn("{}服务的{}配置文件中没有{}分支", getServeName(), getName(), branchName);
        }
    }

    /**
     * @Description : 添加新的路径到branch
     **/
    protected void addPathsToConfig(String branch, String path) {
        this.branches.get(branch).getPaths().add(path);
    }

    /**
     * @Description : 是否展示给使用者
     **/
    public Boolean isDisplay() {
        return this.getClass().getAnnotation(Config.class).show();
    }

    /**
     * @Description : 获取分支名称集合
     **/
    public List<String> getBranchNames() {
        return CollUtil.map(branches.values(), Branch::getName, true);
    }

    /**
     * @Description : 获取默认分支配置项
     * @note : ⚠️ 返回 K - V !
     **/
    public Map<String, String> getDefaultBranchContent() {
        return this.branches.get(SdpPropertiesFinal.DEFAULT_CHAR).getBranchStrMap();
    }

    /**
     * @Description : 获取分支配置项
     * @note : ⚠️ 返回 K - V !
     **/
    public Map<String, String> getBranchContent(String branchName) {
        return this.branches.get(branchName).getBranchStrMap();
    }

    /**
     * @Description : 获取默认配置文件内容
     * @note : ⚠️ 供前端展示使用 !
     **/
    public Map<String, ConfigItem> getDefaultBranchDetails() {
        return this.branches.get(SdpPropertiesFinal.DEFAULT_CHAR).getBranchItemsMap();
    }

    /**
     * @Description : 获取分支配置文件内容
     * @note : An art cell !
     **/
    public Map<String, ConfigItem> getBranchDetails(String branchName) {
        return this.branches.get(branchName).getBranchItemsMap();
    }

    /**
     * @Description : 获取服务安装时候需要的分支配置文件内容
     **/
    public Map<String, ConfigItem> getBranchInstallingConfig(String branchName) {
        Map<String, ConfigItem> itemsMap = this.branches.get(branchName).getBranchItemsMap();
        return itemsMap.entrySet().stream()
                .filter(entry -> ObjectUtil.isNotEmpty(entry.getValue().getLabel())&&entry.getValue().getLabel().contains("user-oriented"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * @Description : 修改配置文件
     * @note : ⚠️ 参数需要传入完整的配置文件内容 !
     **/
    @Override
    public void updateConfig(String branchName, Map<String, String> items) {
        Branch branch = this.branches.get(branchName);
        if(ObjectUtil.isNotNull(branch)) {
            branch.updateContent(items);
        } else {
            log.error("需要修改{}服务的{}配置文件的{}分支不存在", getServeName(), getName(), branchName);
        }
    }

    /**
     * @Description : 修改默认配置文件的内容
     **/
    public void updateDefaultConfig(Map<String, String> items) {
        updateConfig(SdpPropertiesFinal.DEFAULT_CHAR, items);
    }

    /**
     * 获取分支的字典数据
     */
    public List<ConfigItem> getBranchDictionary(String branchName){
        return dictionaryMap.get(branchName);
    }

    /**
     * 判断一个配置项的key是否在字典中
     */
    public Boolean IsConfigItemInDictionary(String branchName,String key){
        List<ConfigItem> list = dictionaryMap.get(branchName);
        List<ConfigItem> items = list.stream().filter(s -> s.getKey().equals(key)).collect(Collectors.toList());
        return ObjectUtil.isNotEmpty(items);
    }

    /**
     * 判断一个配置项的key是否在使用中
     */
    public Boolean IsConfigItemInUse(String branchName,String key){
        List<ConfigItem> items = this.branches.get(branchName).getItems().stream().filter(s -> s.getKey().equals(key)).collect(Collectors.toList());
        return ObjectUtil.isNotEmpty(items);
    }

    public Map<String, ConfigCompare> getConfigCompare(ConfigBranch branch1, ConfigBranch branch2){
        List<ConfigItem> list1 = configDriver.loadConfigItems(branch1);
        List<ConfigItem> list2 = configDriver.loadConfigItems(branch2);

        Map<String, ConfigCompare> map =new HashMap<>();
        for (ConfigItem item : list1) {
            ConfigCompare configCompare = new ConfigCompare();
            configCompare.setKey(item.getKey());
            configCompare.setVersion1(item.getValue());
            configCompare.setVersion2(null);
            configCompare.setOrigin(null);
            map.put(item.getKey(), configCompare);
        }

        for (ConfigItem item : list2) {
            if (map.containsKey(item.getKey())) {
                ConfigCompare configCompare = map.get(item.getKey());
                configCompare.setVersion2(item.getValue());
                map.put(item.getKey(),configCompare);
            } else {
                ConfigCompare configCompare = new ConfigCompare();
                configCompare.setKey(item.getKey());
                configCompare.setVersion1(null);
                configCompare.setVersion2(item.getValue());
                configCompare.setOrigin(null);
                map.put(item.getKey(), configCompare);
            }
        }

        List<ConfigItem> list = dictionaryMap.get(branch1.getBranchName());
        for (String key : map.keySet()) {
            List<ConfigItem> collect = list.stream().filter(s -> s.getKey().equals(key)).collect(Collectors.toList());
            if(ObjectUtil.isNotEmpty(collect)){
                ConfigItem item = collect.get(0);
                ConfigCompare configCompare = map.get(key);
                configCompare.setOrigin(item.getOrigin());
                map.put(key,configCompare);
            }
        }

        return map;
    }

    /**
     * @Description : 获取服务名称
     **/
    public String getServeName(){
        return ObjectUtil.isNull(this.serve) ? null : this.serve.getName();
    }

    /**
     * @Description : 添加分支管理主机
     * @note : ⚠️ 该方法会同步内存和存储中的配置文件分支拓扑图 !
     **/
    public void addBranchHosts(String branchName, String ... hostnames) {
        Branch branch = this.branches.get(branchName);
        if(ObjectUtil.isNotEmpty(branch)) {
            branch.addHosts(hostnames);
        } else {
            log.error("{}服务中的{}配置文件不存在{}分支", serve.getName(), this.getName(), branchName);
        }
    }

    /**
     * @Description : @addBranchHosts 的特殊情况
     **/
    public void addBranchHostsByDefault(String ... hostnames) {
        addBranchHosts(SdpPropertiesFinal.DEFAULT_CHAR, hostnames);
    }

    /**
     * @Description : 删除分支管理主机
     **/
    public void delBranchHosts(String branchName, String ... hostnames) {
        Branch branch = this.branches.get(branchName);
        if(ObjectUtil.isNotEmpty(branch)) {
            branch.delHosts(hostnames);
        } else {
            log.error("{}服务中的{}配置文件不存在{}分支", serve.getName(), this.getName(), branchName);
        }
    }

    public Boolean isDictionaryExists(String branchName){
        Branch branch = this.branches.get(branchName);
        return CollUtil.isNotEmpty(branch.getConfigDictionary());
    }

    /**
     * @Description : @delBranchHosts 的特殊情况
     **/
    public void delBranchHostsByDefault(String ... hostnames) {
        delBranchHosts(SdpPropertiesFinal.DEFAULT_CHAR, hostnames);
    }

    /**
     * @Description : 安装时加载初始化配置文件
     * @note : ⚠️ 需要返回配置文件的全部内容, 该方法的修改会覆盖蓝图中添加的配置 !
     **/
    protected abstract List<BranchModel> initContents(Map<String, Map<String, String>> branches, Blueprint.Serve serve);

    @Getter
    @AllArgsConstructor
    protected static class BranchModel {

        private final String name;

        private final Set<String> hostnames;

        private final Map<String, String> content;

    }

    @AllArgsConstructor
    protected class Branch {

        @Getter
        private final String name;    // 分支配置文件名称

        @Getter
        private final String classpath;   // 分支配置文件在jar包中的相对路径

        @Getter
        private final String type;    // 分支配置文件的类型

        private final ConfigDriver driver;    // 配置文件引擎

        @Getter
        private final List<String> paths = CollUtil.newArrayList();   // 分支配置文件同步路径集合

        @Getter
        private List<ConfigItem> items; // 分支配置文件内容

        @Getter
        private  List<ConfigItem> configDictionary=CollUtil.newArrayList();//分支字典

        @Getter
        private  List<ConfigItem> nonConfigDictionary=CollUtil.newArrayList();//分支没有字典，在这里维护权限

        /**
         * @Description : 构造函数
         * @note : ⚠️ classpath是jar包中默认的配置文件路径 !
         **/
        protected Branch(String name, String classpath) {
            this.name = name;
            this.classpath = classpath;
            this.type = AbstractConfig.this.getClass().getAnnotation(Config.class).type();
            this.driver = AbstractConfig.this.configDriver;
            this.paths.add(AbstractConfig.this.path);
        }

        /**
         * @Description : 初始化分支配置文件内容
         **/
        private void initContent() {
            ConfigBranch branch = getSelfMetadata();
            this.items = this.driver.loadConfigItems(branch);//以安装的服务
            initConfigDictionary();//加载字典以及没有字典的
            // 服务没有安装则使用jar包中的默认配置
            if(CollUtil.isEmpty(this.items)) {
                recover();
            }
        }

        private void initConfigDictionary(){
            String dictionaryPath = classpath + "$.csv";
            String nonDictionaryPath= classpath+".csv";
            if(FileUtil.isResourceExist(dictionaryPath)){//存在字典
                configDictionary = CSVConverter.convertCSV(dictionaryPath);
            }
            if(FileUtil.isResourceExist(nonDictionaryPath)){//存在字典
                nonConfigDictionary = CSVConverter.convertCSV(nonDictionaryPath);
            }
        }

        /**
         * @Description : 分支配置文件添加主机
         * @note : ⚠️ 在扩容的时候使用，添加主机自动同步配置 !
         **/
        private void addHosts(String ... hostnames) {
            driver.extendBranchHosts(getSelfMetadata(), Convert.toSet(String.class, hostnames));
            this.driver.conform(getSelfMetadata(), this.getItems());
        }

        /**
         * @Description : 分支配置文件删除主机
         * @note : ⚠️ 在缩容的时候使用 !
         **/
        private void delHosts(String ... hostnames) {
            driver.abandonBranchHosts(getSelfMetadata(), Convert.toSet(String.class, hostnames));
        }

        /**
         * @Description : 还原分支配置文件
         **/
        private void destroy() {
            // 回调引擎中的销毁函数
            this.driver.destroy(getSelfMetadata());
            recover();
        }

        /**
         * @Description : 配置文件复原为初始化状态 根据字典进行初始化
         * @note : ⚠️ 有json文件读json，没有读本来的配置文件，默认：{"context": "xxxxxxxxxxxx"} !
         **/
        private void recover() {

            String hasDictionaryPath = classpath + "$.csv";//1、假设有字典类型的 按照字典还原
            String DontHasDictionaryPath = classpath + ".csv";

            if(FileUtil.isResourceExist(hasDictionaryPath)){
                this.items = CSVConverter.convertCSV(hasDictionaryPath).stream().filter(ConfigItem::getIsMust).collect(Collectors.toList());
            }
            if(FileUtil.isResourceExist(DontHasDictionaryPath)){
                this.items = CSVConverter.convertCSV(DontHasDictionaryPath).stream().filter(ConfigItem::getIsMust).collect(Collectors.toList());
            }

        }

        /**
         * @Description : 更新配置文件
         * @note : ⚠️ 需要提交全量 !
         **/
        private void updateContent(Map<String, String> items) {
            ConfigBranch branch = getSelfMetadata();
            // 先清空，再全量添加
            this.items.clear();
            items.forEach((k, v) -> {
                ConfigItem item = new ConfigItem();
                if(CollUtil.isNotEmpty(configDictionary)){
                    List<ConfigItem> collect = configDictionary.stream().filter(s -> s.getKey().equals(k)).collect(Collectors.toList());
                    if(ObjectUtil.isNotEmpty(collect)){
                        ConfigItem tmp=collect.get(0) ;
                        BeanUtils.copyProperties(tmp,item);
                    }
                    else {
                        item.setIsInDictionary(false);//用来标记用户添加的字典中不存在的配置
                        item.setCanDelete(true);
                        item.setIsMust(false);
                        item.setIsHidden(false);
                        item.setIsReadOnly(false);
                    }
                    item.setKey(k).setValue(v);
                }
                else {//没有字典
                    List<ConfigItem> collect = nonConfigDictionary.stream().filter(s -> s.getKey().equals(k)).collect(Collectors.toList());
                    if(ObjectUtil.isNotEmpty(collect)){
                        ConfigItem tmp=collect.get(0) ;
                        BeanUtils.copyProperties(tmp,item);
                    }
                    else {//用户自己加的
                        item.setCanDelete(true);
                        item.setIsMust(false);
                        item.setIsHidden(false);
                        item.setIsReadOnly(false);
                    }
                    item.setKey(k).setValue(v);

                }
                this.items.add(item);

            });
            this.driver.conform(branch, this.getItems());   // 可能抛出错误，终止流程。
        }

        /**
         * @Description : 获取实体配置
         * @note : An art cell !
         **/
        public Map<String, ConfigItem> getBranchItemsMap() {
            return CollUtil.toMap(this.getItems(), new LinkedHashMap<>(), ConfigItem::getKey);
        }

        /**
         * @Description : 获取字符串配置
         **/
        private Map<String, String> getBranchStrMap() {
            return MapUtil.map(getBranchItemsMap(), (k, v) -> v.getValue());
        }

        /**
         * @Description : 创建分支自己的engine参数
         * @note : An art cell !
         **/
        private ConfigBranch getSelfMetadata() {
            return new ConfigBranch(this.name, AbstractConfig.this.getName(), getServeName(), this.type, this.getPaths());
        }

    }

}
