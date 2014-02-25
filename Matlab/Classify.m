clear('stats','falseAlarm','accuracy','miss');
for iter = 1:100
    AttPerm = Attributes(randperm(length(Attributes)),:);
    blockSize = ceil(length(Attributes)/10);
    A={};
    for i = 1:9
        A{i} = AttPerm(blockSize*(i-1)+1:blockSize*i,:);
    end
    A{10} = AttPerm(blockSize*9:end,:);

    %%Cross validation 
    for select = 1:10
        train = [];
        test = A{select};
        for i = 1:10
            if select ~= i
                train = [train;A{i}];
            end
        end
        %class = classify(test(:,2:6),train(:,2:6),train(:,7),'linear');
        training = train(:,2:end-3);
        trainingCats = train(:,end);
        coefs = LDA(training, trainingCats);
        
        testing = [ones(size(test,1),1),test(:,2:end-3)];
        scores = testing * coefs(1:2,:)';
        [~,class] = max(scores');
        class = class'-1;
        accuracy(select,1) = sum(class == test(:,end))/length(test);
        falseAlarm(select,1) = sum(class(test(:,end)==0)==1)/length(test);
        miss(select,1)= sum(class(test(:,end)==1)==0)/length(test);
    end
    stats(iter,1) = mean(accuracy);
    stats(iter,2) = mean(falseAlarm);
    stats(iter,3) = mean(miss);
end
mean(stats)

