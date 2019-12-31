package org.joyqueue.broker.network.protocol.support;

import com.google.common.collect.Maps;
import org.joyqueue.network.protocol.annotation.CommonHandler;
import org.joyqueue.network.protocol.annotation.FetchHandler;
import org.joyqueue.network.protocol.annotation.ProduceHandler;
import org.joyqueue.network.transport.command.Command;
import org.joyqueue.network.transport.command.handler.CommandHandler;
import org.joyqueue.network.transport.command.handler.CommandHandlerFactory;
import org.joyqueue.network.transport.command.provider.ExecutorServiceProvider;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;

/**
 * ProtocolCommandHandler
 * author: gaohaoxiang
 * date: 2019/12/26
 */
public class CommandHandlerFactoryWrapper implements CommandHandlerFactory {

    private CommandHandlerFactory delegate;
    private ExecutorService commonThreadPool;
    private ExecutorService fetchThreadPool;
    private ExecutorService produceThreadPool;

    private ConcurrentMap<CommandHandler /** source **/, CommandHandler /** wrapper **/> commandHandlerMap = Maps.newConcurrentMap();

    public CommandHandlerFactoryWrapper(CommandHandlerFactory delegate, ExecutorService commonThreadPool, ExecutorService fetchThreadPool, ExecutorService produceThreadPool) {
        this.delegate = delegate;
        this.commonThreadPool = commonThreadPool;
        this.fetchThreadPool = fetchThreadPool;
        this.produceThreadPool = produceThreadPool;
    }

    @Override
    public CommandHandler getHandler(Command command) {
        CommandHandler commandHandler = delegate.getHandler(command);
        if (commandHandler == null) {
            return null;
        }
        CommandHandler protocolCommandHandler = commandHandlerMap.get(commandHandler);
        if (protocolCommandHandler == null) {
            protocolCommandHandler = initProtocolCommandHandler(commandHandler);
            commandHandlerMap.put(commandHandler, protocolCommandHandler);
        }
        return protocolCommandHandler;
    }

    protected CommandHandler initProtocolCommandHandler(CommandHandler commandHandler) {
        if (commandHandler instanceof ExecutorServiceProvider) {
            return commandHandler;
        }
        if (commandHandler.getClass().getAnnotation(CommonHandler.class) != null) {
            return new CommandHandlerWrapper(commandHandler, commonThreadPool);
        } else if (commandHandler.getClass().getAnnotation(FetchHandler.class) != null) {
            return new CommandHandlerWrapper(commandHandler, fetchThreadPool);
        } else if (commandHandler.getClass().getAnnotation(ProduceHandler.class) != null) {
            return new CommandHandlerWrapper(commandHandler, produceThreadPool);
        } else {
            return new CommandHandlerWrapper(commandHandler, commonThreadPool);
        }
    }
}