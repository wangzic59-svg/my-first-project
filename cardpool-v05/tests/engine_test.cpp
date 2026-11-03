#include "../app/src/main/cpp/engine.hpp"
#include <cassert>
#include <iostream>
#include <limits>
using namespace cardpool;
void near(double a,double b){assert(std::abs(a-b)<1e-10);}
double miss(int slots,int pool,int nonTarget,double odds){if(!slots)return 1;return (1-odds)*miss(slots-1,pool,nonTarget,odds)+(nonTarget?odds*nonTarget/pool*miss(slots-1,pool-1,nonTarget-1,odds):0);}
int main(){
 assert(remaining(10,7)==3);assert(remaining(10,10)==0);
 int failures=0;for(auto args:std::vector<std::pair<int,int>>{{-1,0},{10,-1},{10,11}})try{remaining(args.first,args.second);}catch(const std::invalid_argument&){failures++;}assert(failures==3);
 auto a=predict(1,10,1,5,false);near(a.shop,.5);near(a.slot,.1);near(a.expected,.5);
 auto b=predict(1,10,1,5,true);near(b.shop,1-std::pow(.9,5));
 near(predict(0,0,1,5,false).shop,0);near(predict(10,10,0,5,false).shop,0);near(predict(10,10,1,5,false).shop,1);
 int checks=0;
 for(int pool=5;pool<=15;pool++)for(int target=0;target<=pool;target++)for(double odds:{0.,.1,.25,.75,1.}){
   auto p=predict(target,pool,odds,5,false);near(p.shop,1-miss(5,pool,pool-target,odds));assert(p.shop>=-1e-12&&p.shop<=1+1e-12);checks++;
 }
 failures=0;
 try{predict(1,10,std::numeric_limits<double>::quiet_NaN(),5,false);}catch(const std::invalid_argument&){failures++;}
 try{predict(3,2,.25,5,true);}catch(const std::invalid_argument&){failures++;}
 try{predict(1,2,.25,5,false);}catch(const std::invalid_argument&){failures++;}
 try{predict(1,10,.25,0,false);}catch(const std::invalid_argument&){failures++;}assert(failures==4);
 std::cout<<"PASS C++: "<<checks<<" enumerated probability cases + depletion, invalid inputs and model boundaries\n";
}
