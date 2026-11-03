#pragma once
#include <cmath>
#include <stdexcept>
#include <vector>
namespace cardpool {
inline int remaining(int total,int observed){
    if(total<0||total>10000||observed<0||observed>total)throw std::invalid_argument("观察数量超过卡池总量，或卡池参数无效");
    return total-observed;
}
struct Result {double slot,shop,expected;};
inline Result predict(int target,int pool,double odds,int slots,bool independent){
    if(target<0||pool<0||target>pool||!std::isfinite(odds)||odds<0||odds>1||slots<1||slots>20)throw std::invalid_argument("预测输入无效");
    if(odds==0||target==0)return {0,0,0};
    if(pool==0||(!independent&&pool<slots))throw std::invalid_argument("同费剩余卡池不足，无法使用当前刷新模型");
    const double single=odds*target/pool;
    double noTarget=0;
    if(independent)noTarget=std::pow(1-single,slots);
    else {
        // Number of this-cost slots is binomial; conditional hero draws are
        // hypergeometric. No random sampling and no invented shop results.
        std::vector<double> distribution(slots+1,0);distribution[0]=1;
        for(int n=0;n<slots;n++)for(int k=n+1;k>=0;k--)
            distribution[k]=(k<=n?distribution[k]*(1-odds):0)+(k>0?distribution[k-1]*odds:0);
        double miss=1;
        for(int k=0;k<=slots;k++){
            if(k>0)miss*= (pool-target-(k-1)>0)?double(pool-target-(k-1))/(pool-(k-1)):0;
            noTarget+=distribution[k]*miss;
        }
    }
    return {single,1-noTarget,slots*single};
}
}
