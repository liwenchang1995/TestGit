package com.landray.kmss.sys.xform.fragmentSet.service.spring;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.landray.kmss.common.actions.RequestContext;
import com.landray.kmss.common.forms.IExtendForm;
import com.landray.kmss.common.model.IBaseModel;
import com.landray.kmss.common.service.BaseServiceImp;
import com.landray.kmss.sys.xform.XFormConstant;
import com.landray.kmss.sys.xform.base.model.BaseFormTemplateHistory;
import com.landray.kmss.sys.xform.base.model.EditionManager;
import com.landray.kmss.sys.xform.base.model.SysFormTemplate;
import com.landray.kmss.sys.xform.base.model.SysFormTemplateHistory;
import com.landray.kmss.sys.xform.base.service.ISysFormDbTableService;
import com.landray.kmss.sys.xform.base.service.spring.SysFormGenerateService;
import com.landray.kmss.sys.xform.fragmentSet.exception.FragmentSetRefException;
import com.landray.kmss.sys.xform.fragmentSet.forms.SysFormFragmentSetForm;
import com.landray.kmss.sys.xform.fragmentSet.forms.SysFormFragmentSetHistoryForm;
import com.landray.kmss.sys.xform.fragmentSet.model.SysFormFragmentSet;
import com.landray.kmss.sys.xform.fragmentSet.model.SysFormFragmentSetHistory;
import com.landray.kmss.sys.xform.fragmentSet.service.ISynchronousStatusService;
import com.landray.kmss.sys.xform.fragmentSet.service.ISysFormFragmentSetScopeService;
import com.landray.kmss.sys.xform.fragmentSet.service.ISysFormFragmentSetService;
import com.landray.kmss.sys.xform.fragmentSet.service.meger.ISysFormFragmentSetMergeService;
import com.landray.kmss.sys.xform.fragmentSet.service.meger.MegerContext;
import com.landray.kmss.sys.xform.fragmentSet.service.meger.SynchronousContext;
import com.landray.kmss.sys.xform.service.FileService;
import com.landray.kmss.sys.xform.util.LangCacheManager;
import com.landray.kmss.sys.xform.util.LangUtil;
import com.landray.kmss.util.ArrayUtil;
import com.landray.kmss.util.SpringBeanUtil;
import com.landray.kmss.util.StringUtil;
import com.landray.kmss.util.UserUtil;
import com.landray.kmss.web.filter.security.ConverterContext;
import com.landray.kmss.web.filter.security.ConvertorBase64x;
import com.landray.kmss.web.filter.security.IConvertor;

import bsh.This;

/**
 * 片段集业务接口实现
 * 
 * @author 
 * @version 1.0 2018-05-17
 */
public class SysFormFragmentSetServiceImp extends BaseServiceImp implements ISysFormFragmentSetService {

	private static final Log logger = LogFactory.getLog(This.class);

	private SysFormGenerateService sysFormGenerateService;

	private final IConvertor convert = new ConvertorBase64x();

	/**
	 * @param sysFormGenerateService
	 *            要设置的 sysFormGenerateService
	 */
	public void setSysFormGenerateService(
			SysFormGenerateService sysFormGenerateService) {
		this.sysFormGenerateService = sysFormGenerateService;
	}

	private ISysFormDbTableService sysFormDbTableService;

	public void setSysFormDbTableService(
			ISysFormDbTableService sysFormDbTableService) {
		this.sysFormDbTableService = sysFormDbTableService;
	}

	private FileService fileService;

	public void setFileService(FileService fileService) {
		this.fileService = fileService;
	}
	
	/**
	 * 合并服务
	 */
	private ISysFormFragmentSetMergeService sysFormFragmentSetMegerService;
	

	public ISysFormFragmentSetMergeService getSysFormFragmentSetMegerService() {
		if (sysFormFragmentSetMegerService == null){
			sysFormFragmentSetMegerService = (ISysFormFragmentSetMergeService)SpringBeanUtil.getBean("sysFormFragmentSetMergerService");
		}
		return sysFormFragmentSetMegerService;
	}
	
	/**
	 * 同步状态服务
	 */
	protected ISynchronousStatusService synchronousStatusService;
	
	public ISynchronousStatusService getSynchronousStatusService() {
		if (synchronousStatusService == null){
			synchronousStatusService = (ISynchronousStatusService)SpringBeanUtil.getBean("synchronousStatusService");
		}
		return synchronousStatusService;
	}
	
	/**
	 * 范围服务
	 */
	protected ISysFormFragmentSetScopeService sysFormFragmentSetScopeService;
	
	public ISysFormFragmentSetScopeService getSysFormFragmentSetScopeService() {
		if (sysFormFragmentSetScopeService == null){
			sysFormFragmentSetScopeService = (ISysFormFragmentSetScopeService)SpringBeanUtil.getBean("sysFragmentSetScopeService");
		}
		return sysFormFragmentSetScopeService;
	}


	public IBaseModel convertFormToModel(IExtendForm form, IBaseModel model,
			RequestContext requestContext) throws Exception {
		String designHtml = "";
		if (form instanceof SysFormFragmentSetForm) {
			designHtml = ((SysFormFragmentSetForm) form).getFdDesignerHtml();
		} else if (form instanceof SysFormFragmentSetHistoryForm) {
			designHtml = ((SysFormFragmentSetHistoryForm) form).getFdDesignerHtml();
		}

		if (StringUtil.isNotNull(designHtml)
				&& designHtml.startsWith(convert.getPrefix())) {// xform单独解密
			designHtml = convert.convert(designHtml, new ConverterContext(
					"fdDesignerHtml", requestContext.getRequest()));
			if (form instanceof SysFormFragmentSetForm) {
				((SysFormFragmentSetForm) form)
						.setFdDesignerHtml(designHtml);
			} else if (form instanceof SysFormFragmentSetHistoryForm) {
				((SysFormFragmentSetHistoryForm) form)
						.setFdDesignerHtml(designHtml);
			}
		}

		return super.convertFormToModel(form, model, requestContext);
	}
	
	/**
	 * 片段集版本计算
	 * @param sysFormFragment
	 * @throws Exception
	 */
	private void calculate(SysFormFragmentSet sysFormFragment) throws Exception {
		sysFormFragment.setFdAlterTime(new Date());
		sysFormFragment.setFdAlteror(UserUtil.getUser());
		if (sysFormFragment.getFdIsChanged()
				&& sysFormFragment.getFdDisplayType() == XFormConstant.DISPLAY_DEFINE) {
			if (logger.isDebugEnabled()) {
				logger.debug("片段集内容发生改变，需要从新生成JSP。并计算版本和更新历史版本！");
			}
			sysFormGenerateService.execGenerate(sysFormFragment);
			sysFormFragment.calculateEdition();
			fileService.overWrite(sysFormFragment);
		} else if (sysFormFragment.getFdDisplayType() == XFormConstant.DISPLAY_EXIST) {
			if (logger.isDebugEnabled()) {
				logger.debug("片段集，使用已存在版本！");
			}
			String fileName = sysFormFragment.getFdFormFileName();
			sysFormFragment.clearData();
			sysFormFragment.setFdFormFileName(fileName); // 重设文件地址
		}
		// 自定义表单多语言，设置缓冲信息
		String fdContent = sysFormFragment.getFdMultiLangContent();
		LangCacheManager.putCache(sysFormFragment.getFdFormFileName(), LangUtil
				.convertStringToMap(fdContent));
	}
	
	/**
	 * 新建片段集
	 * @param modelObj
	 * @throws Exception
	 */
	public String add(IBaseModel modelObj) throws Exception {
		calculate((SysFormFragmentSet) modelObj);
		SysFormFragmentSet  sysFormFragmentSet = (SysFormFragmentSet) modelObj;
		BaseFormTemplateHistory lastHistory = EditionManager.getLastHistory(sysFormFragmentSet.getHbmHistries());
		String fdScopeId = sysFormFragmentSet.getFdScopeId();
		//保存片段集使用范围
		getSysFormFragmentSetScopeService().addByFragSetHisId(lastHistory.getFdId(),fdScopeId);
		return super.add(modelObj);
	}
	
	/**
	 * 编辑片段集,若保存为新版本,则重新生成一份缓存文件
	 * 当前片段集和历史片段集的编辑保存都调用此方法
	 * 
	 * @param modelObj 片段集对象
	 * @return 
	 * @throws Exception
	 */
	public void update(IBaseModel modelObj) throws Exception {
		//当前版本
		SysFormFragmentSetHistory sysFormFragmentSetHistory = null;
		if (modelObj instanceof SysFormFragmentSet) {
			SysFormFragmentSet sysFormFragmentset = (SysFormFragmentSet) modelObj;
			calculate(sysFormFragmentset);
			Boolean fdSaveAsNewEdition = sysFormFragmentset.getFdSaveAsNewEdition();
			List hbmHistries = sysFormFragmentset.getHbmHistries();
			if (!fdSaveAsNewEdition && !ArrayUtil.isEmpty(hbmHistries)){
				sysFormFragmentSetHistory = (SysFormFragmentSetHistory)EditionManager.getLastHistory(hbmHistries);
			}
			//维护片段集使用范围
			getSysFormFragmentSetScopeService().
					addOrUpdateByFragmentSetHistory(sysFormFragmentset);
			
		}
		//历史版本
		if (modelObj instanceof SysFormFragmentSetHistory) {
			sysFormFragmentSetHistory = (SysFormFragmentSetHistory) modelObj;
			//维护片段集范围使用
			getSysFormFragmentSetScopeService().
				updateScope(sysFormFragmentSetHistory.getFdId(), sysFormFragmentSetHistory.getFdScopeId());
			if (sysFormFragmentSetHistory.getFdIsChanged()) {
				sysFormFragmentSetHistory.setFdAlterTime(new Date());
				sysFormFragmentSetHistory.setFdAlteror(UserUtil.getUser());
				// 解析HTML代码
				sysFormGenerateService.execGenerate(sysFormFragmentSetHistory);
				// 生成覆盖文件
				fileService.overWrite(sysFormFragmentSetHistory);
				// 自定义表单多语言，设置缓冲信息
				String fdContent = sysFormFragmentSetHistory
						.getFdMultiLangContent();
				LangCacheManager.putCache(sysFormFragmentSetHistory
						.getFdFormFileName(), LangUtil
						.convertStringToMap(fdContent));
			}
		}
		//修改状态为未同步
		if (sysFormFragmentSetHistory != null){
			getSynchronousStatusService().addOrUpdate(sysFormFragmentSetHistory);
		}
		super.update(modelObj);
	}
	
	/**
	 * 删除片段集,若片段集被引用,则不允许删除<br>
	 * 
	 * @param modelObj 片段集对象
	 * @return 若被表单模板引用则抛出异常
	 * @throws Exception
	 */
	public void delete(IBaseModel modelObj) throws Exception {
		SysFormFragmentSet coreModel = (SysFormFragmentSet) modelObj;
		//获取当前版本对应的所有历史版本
		List<SysFormFragmentSetHistory> hbmHistries = coreModel.getHbmHistries();
		for (int i = 0; i < hbmHistries.size(); i++){
			SysFormFragmentSetHistory history = hbmHistries.get(i);
			//获取被引用的表单模板
			List<SysFormTemplateHistory> sysFormTemplateHistorys = history.getSysFormTemplateHistorys();
			if (!ArrayUtil.isEmpty(sysFormTemplateHistorys)){
				throw new FragmentSetRefException();
			}
		}
		
		// 删除表单配置
		sysFormDbTableService.deleteByFormId(modelObj.getFdId());
		// 删除自定义表单多语言缓存信息
		LangCacheManager.removeCache(coreModel.getFdFormFileName());
		
		super.delete(modelObj);
		
		//删除状态表记录
		getSynchronousStatusService().deleteList(hbmHistries);
		//删除范围表
		getSysFormFragmentSetScopeService().deleteList(hbmHistries);
	}
	
	/**
	 * 判断表单是否为最新的历史版本
	 * @param sysFormTemplateHistory
	 * @return
	 */
	public boolean isLatestSysFormTemplateHistoryVersion(SysFormTemplateHistory sysFormTemplateHistory){
		boolean isLatestSysFormTemplateHistory = false;
		if (sysFormTemplateHistory != null){
			SysFormTemplate fdTemplate = sysFormTemplateHistory.getFdTemplate();
			List<SysFormTemplateHistory> hbmHistries = fdTemplate.getHbmHistries();
			BaseFormTemplateHistory lastHistory = EditionManager.getLastHistory(hbmHistries);
			if (lastHistory != null){
				isLatestSysFormTemplateHistory = lastHistory.getFdId().equals(sysFormTemplateHistory.getFdId());
			}
		}
		return isLatestSysFormTemplateHistory;
	}

	/**
	 * 同步片段集到表单中
	 * @param sysFormTemplateHistory
	 * @return
	 * @throws Exception 
	 */
	@Override
	public void updateSynchronous(SynchronousContext synchronousContext) throws Exception {
		
		Map<String,List<Map<String,String>>> conflictMap = getSysFormFragmentSetMegerService().getConfilctControls(synchronousContext);
		synchronousContext.setConflictMap(conflictMap);
		
		//历史片段集
		SysFormFragmentSetHistory src = (SysFormFragmentSetHistory)synchronousContext.getSrc();
		//表单模板
		List targets = synchronousContext.getTargets();
		//表单历史版本id
		String fdTemplateHistoryId = "";
		
		MegerContext megerContext = new MegerContext();
		megerContext.setFragmentSet(src);
		
		//合并片段集和历史表单模板
		for (Object baseModel : targets){
			SysFormTemplateHistory templateHistory = (SysFormTemplateHistory) baseModel;
			fdTemplateHistoryId = templateHistory.getFdId();
			//判断表单是否为最新的历史版本
			boolean isLatestVersion = isLatestSysFormTemplateHistoryVersion(templateHistory);
			
			//有冲突的不进行合并
			if (conflictMap.get(fdTemplateHistoryId) != null){
				continue;
			}
			
			megerContext.setTemplate(templateHistory);
			//合并html
			String fdDesignerHtml = getSysFormFragmentSetMegerService().execMegerHtml(megerContext);
			templateHistory.setFdDesignerHtml(fdDesignerHtml);
			templateHistory.setFdAlterTime(new Date());
			//合并缓存文件
			getSysFormFragmentSetMegerService().execMegerJsp(megerContext);
			//合并数据字典
			String megerMetadataXml = getSysFormFragmentSetMegerService().execMegerXml(megerContext);
			templateHistory.setFdMetadataXml(megerMetadataXml);
			//合并多语言
			String megerMultiContext = getSysFormFragmentSetMegerService().execMegerMultiLang(megerContext);
			templateHistory.setFdMultiLangContent(megerMultiContext);
			
			//覆盖表单文件
			LangCacheManager.clear();
			fileService.overWrite(templateHistory);
			try {
				//如果引用的表单模板是最新的历史版本,要更新当前版本
				if(isLatestVersion){
					SysFormTemplate template = templateHistory.getFdTemplate();
					template.setFdAlterTime(new Date());
					template.setFdDesignerHtml(fdDesignerHtml);
					template.setFdMetadataXml(megerMetadataXml);
					template.setFdMultiLangContent(megerMultiContext);
					super.update(template);
					// 自定义表单多语言，设置缓冲信息
					String fdContent = template.getFdMultiLangContent();
					LangCacheManager.putCache(template.getFdFormFileName(), LangUtil
							.convertStringToMap(fdContent));
				}
				//修改关联关系
				updateRelation(synchronousContext, templateHistory);
				super.update(templateHistory);
				
			} catch (Exception e) {
				e.printStackTrace();
			}
			
		}
	}
	
	/**
	 * 更新历史片段集和历史表单的关系
	 * @param sysFormFragmentSetHistory 同步的历史片段集
	 * @param sysFormTemplateHistory 引用的表单模板
	 */
	public void updateRelation(SynchronousContext synchronousContext,SysFormTemplateHistory sysFormTemplateHistory){
		//同步前引用的历史片段集id
		String fdId = synchronousContext.getFdRefId();
		//同步后引用的历史片段集
		SysFormFragmentSetHistory sysFormFragmentSetHistory = (SysFormFragmentSetHistory)synchronousContext.getSrc();
		String refId = "";
		
		//获取表单所关联的片段集
		List sysFormFragmentSetHistorys = sysFormTemplateHistory.getSysFormFragmentSetHistorys();
		
		for (int i = 0,size = sysFormFragmentSetHistorys.size(); i < size; i++){
			SysFormFragmentSetHistory fragmenSetHis = (SysFormFragmentSetHistory) sysFormFragmentSetHistorys.get(i);
			refId = fragmenSetHis.getFdId();
			if (refId.equals(fdId)){
//				sysFormFragmentSetHistorys.add(i, sysFormFragmentSetHistory);
				sysFormFragmentSetHistorys.remove(i);
				sysFormFragmentSetHistorys.add(sysFormFragmentSetHistory);
			}
		}
	}
}
