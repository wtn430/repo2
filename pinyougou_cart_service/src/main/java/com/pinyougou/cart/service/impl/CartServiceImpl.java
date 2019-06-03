package com.pinyougou.cart.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.pinyougou.cart.service.CartService;
import com.pinyougou.mapper.TbItemMapper;
import com.pinyougou.pojo.TbItem;
import com.pinyougou.pojo.TbOrderItem;
import com.pinyougou.pojogroup.Cart;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
@Service
public class CartServiceImpl implements CartService{

    @Autowired
    private TbItemMapper tbItemMapper;

    @Override
    public List<Cart> addGoodsToCartList(List<Cart> cartList, Long itemId, Integer num) {
        //1.根据商品SKU的id信息查询SKU商品信息
        TbItem item = tbItemMapper.selectByPrimaryKey(itemId);
        //防止加入过程中商品信息变更
        if(item == null){
            throw new RuntimeException("该商品不存在");
        }
        if(!item.getStatus().equals("1")){
            throw new RuntimeException("商品状态无效");
        }
        //2.根据SKU商品信息获取商家ID、名称
        String sellerId = item.getSellerId();
        String seller = item.getSeller();
        //3.根据商家ID判断购物车中是否存在该商家的购物车
        Cart cart = searchCartBySellerId(cartList, sellerId);
        //4.如果不存在商家的购物车
        if(cart==null){
            //4.1新建购物车对象
            cart = new Cart();
            cart.setSellerId(sellerId);
            cart.setSellerName(seller);
            //创建购物车明细列表
            List<TbOrderItem> orderItemList = new ArrayList<>();
            //创建订单明细
            TbOrderItem orderItem = createOrderItem(item, num);
            orderItemList.add(orderItem);
            cart.setOrderItemList(orderItemList);
            //4.2将新建的购物车对象添加到购物车列表
            cartList.add(cart);
        }else{
            //5.如果存在商家的购物车
            TbOrderItem orderItem = searchOrderItemByItemId(cart.getOrderItemList(), itemId);
            //5.1查询订单明细列表中是否存在该商品
            if(orderItem == null){
                //该商品不存在时，新建订单明细
                orderItem = createOrderItem(item,num);
                //添加到购物车
                cart.getOrderItemList().add(orderItem);
            }else {
                //该商品已存在时，在原有购物车上增加数量，更改金额
                orderItem.setNum(orderItem.getNum() + num);
                orderItem.setTotalFee(new BigDecimal(item.getPrice().doubleValue() * orderItem.getNum()));
                //如果数量操作后<=0,移除购物车明细
                if(orderItem.getNum() <= 0){
                    cart.getOrderItemList().remove(orderItem);
                }
                //如果移除后cart的明细数量为0，将此购物车移除
                if(cart.getOrderItemList().size() == 0){
                    cartList.remove(cart);
                }
            }

        }
        return cartList;
    }

    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public List<Cart> findCartListFromRedis(String username) {
        List<Cart> cartList = (List<Cart>) redisTemplate.boundHashOps("cartList").get(username);
        if(cartList == null){
            cartList = new ArrayList<>();
        }
        System.out.println("从redis中查询购物车---"+username);
        return cartList;
    }

    @Override
    public void saveCartListToRedis(String username, List<Cart> cartList) {
        System.out.println("向redis中存入购物车---"+username);
        redisTemplate.boundHashOps("cartList").put(username,cartList);
    }

    /**
     * 合并购物车
     * @param cartList1
     * @param cartList2
     * @return
     */
    @Override
    public List<Cart> mergeCartList(List<Cart> cartList1, List<Cart> cartList2) {
        System.out.println("合并购物车");
        //遍历2添加到1中    不能使用add进行合并
        for (Cart cart : cartList2) {
            //遍历订单详情,添加到1
            for (TbOrderItem orderItem : cart.getOrderItemList()) {
                cartList1 = addGoodsToCartList(cartList1,orderItem.getItemId(),orderItem.getNum());
            }
        }
        return cartList1;
    }


    /**
     * 根据商家id查询购物车对象，返回该商家的购物车对象
     * @param cartList
     * @param sellerId
     * @return
     */
    private Cart searchCartBySellerId(List<Cart> cartList,String sellerId){
        //遍历购物车列表查询
        for (Cart cart : cartList) {
            if(cart.getSellerId().equals(sellerId)){
                return cart;
            }
        }
        return null;
    }

    /**
     * 创建订单明细
     * @param item
     * @param num
     * @return
     */
    private TbOrderItem createOrderItem(TbItem item,Integer num){

        if(num <= 0 ){
            throw new RuntimeException("数量非法");
        }
        //创建订单明细对象
        TbOrderItem tbOrderItem = new TbOrderItem();
        tbOrderItem.setGoodsId(item.getGoodsId());
        tbOrderItem.setItemId(item.getId());
        tbOrderItem.setNum(num);
        tbOrderItem.setPicPath(item.getImage());
        tbOrderItem.setPrice(item.getPrice());
        tbOrderItem.setSellerId(item.getSellerId());
        tbOrderItem.setTitle(item.getTitle());
        tbOrderItem.setTotalFee(new BigDecimal(item.getPrice().doubleValue()*num));
        return tbOrderItem;
    }

    /**
     * 根据SKU的id在购物车明细列表中查询购物车明细对象
     * @param orderItemList
     * @param itemId
     * @return
     */
    public TbOrderItem searchOrderItemByItemId(List<TbOrderItem> orderItemList,Long itemId){
        for (TbOrderItem orderItem : orderItemList) {
            //值的比较，使用longValue()     否则为比较地址值
            if(orderItem.getItemId().longValue() == itemId.longValue()){
                return orderItem;
            }
        }
        return null;
    }
}
