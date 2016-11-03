package com.khartec.waltz.service.physical_flow;

import com.khartec.waltz.common.SetUtilities;
import com.khartec.waltz.data.physical_flow.PhysicalFlowDao;
import com.khartec.waltz.data.physical_specification.PhysicalSpecificationDao;
import com.khartec.waltz.model.EntityKind;
import com.khartec.waltz.model.EntityReference;
import com.khartec.waltz.model.Severity;
import com.khartec.waltz.model.changelog.ChangeLog;
import com.khartec.waltz.model.changelog.ImmutableChangeLog;
import com.khartec.waltz.model.command.CommandOutcome;
import com.khartec.waltz.model.dataflow.DataFlow;
import com.khartec.waltz.model.dataflow.ImmutableDataFlow;
import com.khartec.waltz.model.datatype.DataType;
import com.khartec.waltz.model.physical_flow.*;
import com.khartec.waltz.model.physical_specification.PhysicalSpecification;
import com.khartec.waltz.service.changelog.ChangeLogService;
import com.khartec.waltz.service.data_flow.DataFlowService;
import com.khartec.waltz.service.data_flow_decorator.DataFlowDecoratorService;
import com.khartec.waltz.service.data_type.DataTypeService;
import com.khartec.waltz.service.settings.SettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

import static com.khartec.waltz.common.Checks.checkNotNull;
import static com.khartec.waltz.common.StringUtilities.mkSafe;


@Service
public class PhysicalFlowService {

    private final PhysicalFlowDao physicalFlowDao;
    private final PhysicalSpecificationDao physicalSpecificationDao;
    private final ChangeLogService changeLogService;
    private final DataFlowService dataFlowService;
    private final DataFlowDecoratorService dataFlowDecoratorService;
    private final DataTypeService dataTypeService;
    private final SettingsService settingsService;

    private final static String DEFAULT_DATATYPE_CODE_SETTING_NAME = "settings.data-type.default-code";


    @Autowired
    public PhysicalFlowService(ChangeLogService changeLogService,
                               DataFlowService dataFlowService,
                               DataFlowDecoratorService dataFlowDecoratorService,
                               DataTypeService dataTypeService,
                               PhysicalFlowDao physicalDataFlowDao,
                               PhysicalSpecificationDao physicalSpecificationDao,
                               SettingsService settingsService) {
        checkNotNull(changeLogService, "changeLogService cannot be null");
        checkNotNull(dataFlowService, "dataFlowService cannot be null");
        checkNotNull(dataFlowDecoratorService, "dataFlowDecoratorService cannot be null");
        checkNotNull(dataTypeService, "dataTypeService cannot be null");
        checkNotNull(physicalDataFlowDao, "physicalFlowDao cannot be null");
        checkNotNull(physicalSpecificationDao, "physicalSpecificationDao cannot be null");
        checkNotNull(settingsService, "settingsService cannot be null");

        this.changeLogService = changeLogService;
        this.dataFlowService = dataFlowService;
        this.dataFlowDecoratorService = dataFlowDecoratorService;
        this.dataTypeService = dataTypeService;
        this.physicalFlowDao = physicalDataFlowDao;
        this.physicalSpecificationDao = physicalSpecificationDao;
        this.settingsService = settingsService;
    }


    public List<PhysicalFlow> findByEntityReference(EntityReference ref) {
        checkNotNull(ref, "ref cannot be null");
        return physicalFlowDao.findByEntityReference(ref);
    }


    public List<PhysicalFlow> findBySpecificationId(long specificationId) {
        return physicalFlowDao.findBySpecificationId(specificationId);
    }


    public PhysicalFlow getById(long id) {
        return physicalFlowDao.getById(id);
    }


    public PhysicalFlowDeleteCommandResponse delete(PhysicalFlowDeleteCommand command, String username) {
        checkNotNull(command, "command cannot be null");

        PhysicalFlow flow = physicalFlowDao.getById(command.flowId());
        CommandOutcome commandOutcome = CommandOutcome.SUCCESS;
        String responseMessage = null;
        boolean isSpecificationUnused = false;

        if (flow == null) {
            commandOutcome = CommandOutcome.FAILURE;
            responseMessage = "Flow not found";
        } else {
            int deleteCount = physicalFlowDao.delete(command.flowId());

            if (deleteCount == 0) {
                commandOutcome = CommandOutcome.FAILURE;
                responseMessage = "This flow cannot be deleted as it is being used in a lineage";
            } else {
                isSpecificationUnused = !physicalSpecificationDao.isUsed(flow.specificationId());
            }
        }


        // log changes against source and target entities
        if (commandOutcome == CommandOutcome.SUCCESS) {
            PhysicalSpecification specification = physicalSpecificationDao.getById(flow.specificationId());

            logChange(username,
                    specification.owningEntity(),
                    String.format("Physical flow: %s, from: %s, to: %s deleted.",
                            specification.name(),
                            specification.owningEntity().safeName(),
                            flow.target().safeName()));

            logChange(username,
                    flow.target(),
                    String.format("Physical flow: %s, from: %s deleted.",
                            specification.name(),
                            specification.owningEntity().safeName()));

            logChange(username,
                    specification.owningEntity(),
                    String.format("Physical flow: %s, to: %s deleted.",
                            specification.name(),
                            flow.target().safeName()));
        }


        return ImmutablePhysicalFlowDeleteCommandResponse.builder()
                .originalCommand(command)
                .entityReference(EntityReference.mkRef(EntityKind.PHYSICAL_FLOW, command.flowId()))
                .outcome(commandOutcome)
                .message(Optional.ofNullable(responseMessage))
                .isSpecificationUnused(isSpecificationUnused)
                .build();
    }


    public PhysicalFlowCreateCommandResponse create(PhysicalFlowCreateCommand command, String username) {
        checkNotNull(command, "command cannot be null");
        checkNotNull(username, "username cannot be null");

        //check we have a logical data flow
        ensureLogicalDataFlow(command.specification().owningEntity(), command.targetEntity());

        long specId = command
                .specification()
                .id()
                .orElseGet(() -> physicalSpecificationDao.create(command.specification()));

        PhysicalFlow flow = ImmutablePhysicalFlow.builder()
                .specificationId(specId)
                .basisOffset(command.flowAttributes().basisOffset())
                .frequency(command.flowAttributes().frequency())
                .transport(command.flowAttributes().transport())
                .description(mkSafe(command.flowAttributes().description()))
                .target(command.targetEntity())
                .build();

        long flowId = physicalFlowDao.create(flow);


        logChange(username,
                command.specification().owningEntity(),
                String.format("Creating physical flow (%s) to: %s",
                        command.specification().name(),
                        command.targetEntity().safeName()));

        logChange(username,
                command.targetEntity(),
                String.format("Creating physical flow (%s) from: %s",
                        command.specification().name(),
                        command.specification().owningEntity().safeName()));

        logChange(username,
                EntityReference.mkRef(EntityKind.PHYSICAL_SPECIFICATION, specId),
                String.format("Creating physical flow (%s) from: %s, to %s",
                        command.specification().name(),
                        command.specification().owningEntity().safeName(),
                        command.targetEntity().safeName()));


        return ImmutablePhysicalFlowCreateCommandResponse.builder()
                .originalCommand(command)
                .outcome(CommandOutcome.SUCCESS)
                .entityReference(EntityReference.mkRef(EntityKind.PHYSICAL_FLOW, flowId))
                .build();
    }


    private void ensureLogicalDataFlow(EntityReference source, EntityReference target) {
        // only ensure logical flow if both entities are applications
        if(!(source.kind().equals(EntityKind.APPLICATION)
                && target.kind().equals(EntityKind.APPLICATION))) {
            return;
        } else {
            DataFlow dataFlow = dataFlowService.findBySourceAndTarget(source, target);
            if(dataFlow == null) {
                Optional<String> defaultDataTypeCode = settingsService.getValue(DEFAULT_DATATYPE_CODE_SETTING_NAME);
                if(!defaultDataTypeCode.isPresent()) {
                    throw new IllegalStateException("No default datatype code  (" + DEFAULT_DATATYPE_CODE_SETTING_NAME + ") in settings table");
                }

                // we need to create a flow with an unknown data type
                DataFlow createdFlow = dataFlowService.addFlow(ImmutableDataFlow.builder()
                        .source(source)
                        .target(target)
                        .build());

                // add decorators
                DataType defaultDataType = dataTypeService.getByCode(defaultDataTypeCode.get());
                EntityReference dataTypeRef = EntityReference.mkRef(EntityKind.DATA_TYPE, defaultDataType.id().get(), defaultDataType.name());
                dataFlowDecoratorService.addDecorators(createdFlow.id().get(), SetUtilities.fromArray(dataTypeRef));
            }
        }
    }


    private void logChange(String userId,
                           EntityReference ref,
                           String message) {

        ChangeLog logEntry = ImmutableChangeLog.builder()
                .parentReference(ref)
                .message(message)
                .severity(Severity.INFORMATION)
                .userId(userId)
                .build();

        changeLogService.write(logEntry);
    }


}
